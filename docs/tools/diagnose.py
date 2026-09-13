# -*- coding: utf-8 -*-
"""诊断：对比原始备份与当前 docx，确认内容损失范围，并测试段落切分的正确性。"""
import re
import html
import zipfile

CUR = r"E:\SoftwareEngineeringHomework\UniversityManageSystem\docs\原始材料文本\实验一_基于工程意图的软件迭代开发_实验报告模板.docx"
BAK = r"E:\SoftwareEngineeringHomework\UniversityManageSystem\docs\原始材料文本\实验一_基于工程意图的软件迭代开发_实验报告模板_原始备份.docx"
OUT = r"C:\Users\31028\AppData\Local\Temp\dsh-LTcJg5\diag.txt"


def naive_split(text):
    """正则非贪婪切分（之前在 fix_report.py 中使用的方法）"""
    return re.findall(r"<w:p(?:\s[^>]*)?>.*?</w:p>", text, re.S)


def balanced_split(text):
    """平衡扫描切分（正确方法）"""
    res = []
    i = 0
    while True:
        m = re.search(r"<w:p(?:\s[^>]*)?>", text[i:])
        if not m:
            break
        start = i + m.end()
        depth = 1
        j = start
        while depth > 0:
            nxt = re.search(r"<w:p(?:\s[^>]*)?>|</w:p>", text[j:])
            if not nxt:
                break
            if nxt.group(0).startswith("</"):
                depth -= 1
            else:
                depth += 1
            j += nxt.end()
        res.append((i + m.start(), j, text[start:j - len("</w:p>")] if depth == 0 else text[start:j]))
        i = j
    return res


lines = []
for label, path in (("原始备份", BAK), ("当前文件", CUR)):
    z = zipfile.ZipFile(path)
    xml = z.read("word/document.xml").decode("utf-8")
    lines.append("### %s" % label)
    lines.append("  document.xml 字节数: %d" % len(xml.encode("utf-8")))
    lines.append("  <w:p> 出现次数: %d" % len(re.findall(r"<w:p(?:\s[^>]*)?>", xml)))
    lines.append("  </w:p> 出现次数: %d" % len(re.findall(r"</w:p>", xml)))
    lines.append("  非贪婪切分段数: %d" % len(naive_split(xml)))
    lines.append("  平衡扫描段数: %d" % len(balanced_split(xml)))
    for kw in ("Spring Boot", "Vue 3", "MyBatis", "MySQL", "控制台", "报名"):
        lines.append("  关键词 %-12s 出现 %d 次" % (kw, xml.count(kw)))
    lines.append("")

open(OUT, "w", encoding="utf-8").write("\n".join(lines))
print("输出:", OUT)
