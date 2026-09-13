# -*- coding: utf-8 -*-
"""
脚本用途：校验修改后的实验报告 docx 是否完好、技术栈表述是否已全部替换。

检查项：
  1. docx 是否为合法 zip 包，必需条目是否齐全；
  2. word/document.xml 是否为合法 XML（用标准库解析，能解析说明没有破坏标签结构）；
  3. 段落数是否与原始备份一致（防止空段落被吞）；
  4. 旧技术栈关键词是否已清除、新表述是否存在；
  5. 输出 4.1、4.2、2.2 的最终文字，便于人工确认。
"""
import re
import html
import zipfile
import xml.etree.ElementTree as ET

CUR = r"E:\SoftwareEngineeringHomework\UniversityManageSystem\docs\原始材料文本\实验一_基于工程意图的软件迭代开发_实验报告模板.docx"
BAK = CUR.replace(".docx", "_原始备份.docx")
OUT = r"E:\SoftwareEngineeringHomework\UniversityManageSystem\build\verify.txt"

lines = []


def load(path):
    z = zipfile.ZipFile(path)
    return z, z.read("word/document.xml").decode("utf-8")


z_cur, xml_cur = load(CUR)
z_bak, xml_bak = load(BAK)

# 1. zip 完整性
bad = z_cur.testzip()
lines.append("1) zip 完整性: %s" % ("正常" if bad is None else "损坏于 " + str(bad)))
lines.append("   条目数: 当前 %d / 备份 %d" % (len(z_cur.namelist()), len(z_bak.namelist())))
need = ["word/document.xml", "word/styles.xml", "[Content_Types].xml"]
for n in need:
    lines.append("   必需条目 %-24s %s" % (n, "存在" if n in z_cur.namelist() else "缺失!"))

# 2. XML 合法性
try:
    ET.fromstring(xml_cur.encode("utf-8"))
    lines.append("2) document.xml 解析: 合法 XML")
except ET.ParseError as e:
    lines.append("2) document.xml 解析: 解析失败 -> %s" % e)

# 3. 段落数一致
p_cur = len(re.findall(r"<w:p(?:\s[^>]*)?>", xml_cur))
p_bak = len(re.findall(r"<w:p(?:\s[^>]*)?>", xml_bak))
lines.append("3) 段落数: 当前 %d / 备份 %d  -> %s" % (p_cur, p_bak, "一致" if p_cur == p_bak else "不一致!"))

# 4. 关键词检查
text_cur = "".join(html.unescape(x) for x in re.findall(r"<w:t[^>]*>(.*?)</w:t>", xml_cur, re.S))
lines.append("4) 关键词检查:")
for kw in ("Spring Boot", "SpringBoot", "Vue 3", "Vue Router", "Pinia", "Element Plus",
           "Axios", "MyBatis", "Controller", "Service", "Mapper"):
    lines.append("   旧技术栈 %-14s 剩余 %d 次 %s" % (kw, text_cur.count(kw), "" if text_cur.count(kw) == 0 else "<-- 需处理"))
for kw in ("控制台菜单", "JDBC", "界面层", "业务逻辑层", "数据访问层"):
    lines.append("   新表述   %-14s 出现 %d 次" % (kw, text_cur.count(kw)))

# 正文完整性抽查（防止替换时误删正文）
for kw in ("REQ-01", "REQ-06", "activity_registration", "学生查看活动并报名", "报名记录"):
    lines.append("   正文抽查 %-22s 出现 %d 次" % (kw, text_cur.count(kw)))

# 5. 关键段落最终文字
lines.append("")
lines.append("5) 修改后的关键段落:")
for para in re.findall(r"<w:p(?:\s[^>]*)?>.*?</w:p>", xml_cur, re.S):
    t = "".join(html.unescape(x) for x in re.findall(r"<w:t[^>]*>(.*?)</w:t>", para, re.S))
    if t.startswith("系统采用") or t.startswith("本系统核心业务流程") or t.startswith("系统应支持用户注册"):
        lines.append("   >> " + t[:400])
        lines.append("")

open(OUT, "w", encoding="utf-8").write("\n".join(lines))
print("输出:", OUT)
