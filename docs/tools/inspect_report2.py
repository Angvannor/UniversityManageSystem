# -*- coding: utf-8 -*-
"""
文件：docs/tools/inspect_report2.py
用途：把「实验二_V2.0_实验报告模板.docx」的内部结构 dump 成可读文本，
      供填写前规划使用。

为什么需要它：
  docx 里的正文是 word/document.xml，段落与表格混在一起、还夹着大量样式标记。
  直接看 XML 很难判断"第几段该填什么"。
  本脚本按**文档顺序**把顶层元素列出来（段落给出行号、样式、文字；
  表格给出每个单元格的文字），这样就能精确知道要改哪里。

用法：
  python docs/tools/inspect_report2.py            # 打印结构
  python docs/tools/inspect_report2.py > out.txt  # 存文件
"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DOCX = os.path.join(ROOT, "docs", "作业2", "新建文件夹 (10)", "实验二_V2.0_实验报告模板.docx")

# 命令行可以指定别的 docx（例如填写后生成的报告），便于对比模板与结果
if len(sys.argv) > 1:
    DOCX = sys.argv[1]

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


def para_text(p):
    """取一个段落里的纯文字（拼接所有 w:t）。"""
    return "".join(t.text or "" for t in p.iter(W + "t"))


def para_style(p):
    """取段落样式名（w:pPr/w:pStyle 的 w:val）。"""
    ppr = p.find(W + "pPr")
    if ppr is None:
        return ""
    st = ppr.find(W + "pStyle")
    return st.get(W + "val") if st is not None else ""


def main():
    with zipfile.ZipFile(DOCX) as z:
        xml = z.read("word/document.xml")

    root = ET.fromstring(xml)
    body = root.find(W + "body")

    top_index = 0          # 顶层元素序号
    para_index = 0         # 只数段落的序号
    for child in body:
        tag = child.tag.replace(W, "")
        if tag == "p":
            text = para_text(child)
            style = para_style(child)
            print(f"[P{para_index:03d}] (top {top_index:03d}) style={style or '-':<12} {text}")
            para_index += 1
            top_index += 1
        elif tag == "tbl":
            rows = child.findall(W + "tr")
            print(f"[TBL] (top {top_index:03d}) 表格，{len(rows)} 行")
            for ri, tr in enumerate(rows):
                cells = tr.findall(W + "tc")
                vals = []
                for tc in cells:
                    cell_text = "".join(para_text(p) for p in tc.findall(W + "p"))
                    vals.append(cell_text.strip())
                print(f"        row{ri}: " + " | ".join(vals))
            top_index += 1
        else:
            print(f"[{tag}] (top {top_index:03d})")
            top_index += 1


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
