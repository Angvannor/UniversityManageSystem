# -*- coding: utf-8 -*-
"""
文件：docs/tools/verify_report2.py
用途：校验填写后的实验二报告 docx 是否完整、内容是否真的进去了。

为什么单独写一个校验脚本：
  docx 的填写是**结构化修改 XML**，出错方式往往是"静默"的 ——
  标签没匹配上、空段落不够导致内容被丢弃、多行文字产生嵌套 <w:t>……
  这些问题不会让脚本报错，只会让报告里少几段话。
  本脚本按"预期值"逐项核对，把这类问题变成显式失败。

  实际开发中就靠它（的思路）发现过两个问题：
    ① 反思第 4 问的标签因为全角引号写法不同而没匹配上，内容没被填入；
    ② 几个小结的文字比模板预留的空段落多，多出来的段落被静默丢弃。

用法：
  python docs/tools/verify_report2.py
退出码：0 表示全部通过；1 表示有不符项。
"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "docs", "作业2", "实验二_V2.0_实验报告.docx")

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"

# 期望的表格行数（含表头）：表头关键词 -> (模板行数, 期望行数)
EXPECTED_TABLES = [
    ("实验项目", 5, 5),          # 封面
    ("主要承担任务", 4, 2),       # 一、组内分工（单人组 1 行）
    ("角色回答要点", 5, 7),       # 三、学生访谈（6 条）
    ("角色回答要点", 5, 11),      # 三、教师访谈（10 条）
    ("角色回答要点", 5, 7),       # 三、管理员访谈（6 条）
    ("候选需求或规则", 9, 19),    # 四、需求提取（18 条）
    ("明确不能执行的操作", 4, 4), # 五、权限边界（3 个角色）
    ("用户故事", 9, 17),          # 六、用户故事（16 条）
    ("受影响模块", 7, 9),         # 七、影响分析（8 项）
    ("关键提交或分支", 7, 13),    # 九、Git 过程（12 行）
    ("测试场景", 9, 31),          # 十、测试（30 条代表性用例）
    ("使用的AI工具", 5, 5),       # 十一、AI 使用记录（4 行）
    ("电子签名", 4, 2),           # 十三、组员确认（1 行）
]

# 必须出现在报告里的关键内容（抽查）
MUST_CONTAIN = [
    "帅子夏", "HBK组", "校园活动管理系统",
    "实验室开放日",              # T8 的场景
    "审核通过 ≠ 正式参加",        # 本轮核心规则
    "进入候补的时间",            # 候补排序依据
    "uk_reg_student_activity",   # 唯一约束不变
    "3005", "3006", "2003",      # 新增错误码
    "TC-01", "TC-30",            # 测试用例编号
    "git",                       # 仓库地址是英文
]


def para_text(p):
    return "".join(t.text or "" for t in p.iter(W + "t"))


def main():
    if not os.path.exists(OUT):
        print(f"❌ 报告文件不存在：{OUT}")
        print("   请先运行：python docs/tools/fill_report2.py")
        return 1

    failures = []

    # ---------- 1. docx 结构完整性 ----------
    with zipfile.ZipFile(OUT) as z:
        bad = z.testzip()
        if bad:
            failures.append(f"zip 损坏：{bad}")
        names = set(z.namelist())
        for required in ("word/document.xml", "word/styles.xml",
                         "[Content_Types].xml", "word/_rels/document.xml.rels"):
            if required not in names:
                failures.append(f"缺少内部文件：{required}")
        xml = z.read("word/document.xml")

    root = ET.fromstring(xml)
    body = root.find(W + "body")
    print(f"✅ docx 结构完整（{len(names)} 个内部文件）")

    # ---------- 2. 表格行数 ----------
    actual = []
    for tbl in body.iter(W + "tbl"):
        trs = tbl.findall(W + "tr")
        header = "|".join(
            "".join(para_text(p) for p in tc.findall(W + "p")).strip()
            for tc in trs[0].findall(W + "tc")
        ) if trs else ""
        actual.append((header, len(trs)))

    used = [False] * len(actual)
    print("\n表格行数核对：")
    for keyword, tpl_rows, want in EXPECTED_TABLES:
        hit = -1
        for i, (header, rows) in enumerate(actual):
            if not used[i] and keyword in header:
                hit = i
                break
        if hit < 0:
            failures.append(f"找不到表头含「{keyword}」的表格")
            print(f"  ❌ 表头含「{keyword}」的表格：未找到")
            continue
        used[hit] = True
        rows = actual[hit][1]
        ok = rows == want
        if not ok:
            failures.append(f"表格「{keyword}」行数 {rows}，期望 {want}")
        print(f"  {'✅' if ok else '❌'} {keyword:<12} 模板 {tpl_rows} 行 -> 实际 {rows} 行（期望 {want}）")

    # ---------- 3. 关键内容抽查 ----------
    full = "".join(para_text(p) for p in body.iter(W + "p"))
    # 表格单元格里的文字也在 w:p 里，所以上面这一句已经覆盖了表格
    print("\n关键内容抽查：")
    for text in MUST_CONTAIN:
        ok = text in full
        if not ok:
            failures.append(f"报告中找不到关键内容：{text}")
        print(f"  {'✅' if ok else '❌'} {text}")

    # ---------- 4. 不能残留的 markdown 记号 ----------
    print("\nMarkdown 残留检查：")
    artifacts = []
    for pattern, desc in [(r"\*\*", "加粗 **"),
                          (r"^#{1,6} ", "标题 #"),
                          (r"`", "反引号 `")]:
        hits = re.findall(pattern, full, re.M)
        if hits:
            artifacts.append(f"{desc}（{len(hits)} 处）")
    if artifacts:
        failures.extend(f"报告里残留 markdown 记号：{a}" for a in artifacts)
        print("  ❌ " + "、".join(artifacts))
    else:
        print("  ✅ 没有残留 markdown 记号")

    # ---------- 5. 组员确认是否留空待签 ----------
    print("\n组员确认：")
    found_confirm = False
    for tbl in body.iter(W + "tbl"):
        trs = tbl.findall(W + "tr")
        if not trs:
            continue
        # 注意要把表头整行的所有单元格拼起来再判断 ——
        # 「电子签名」在第 2 格，只看第 1 格会找不到（这里踩过一次）
        header = "|".join(
            "".join(para_text(p) for p in tc.findall(W + "p"))
            for tc in trs[0].findall(W + "tc")
        )
        if "电子签名" not in header:
            continue
        found_confirm = True
        cells = trs[1].findall(W + "tc") if len(trs) > 1 else []
        name = "".join(para_text(p) for p in cells[0].findall(W + "p")).strip() if cells else ""
        sign = "".join(para_text(p) for p in cells[1].findall(W + "p")).strip() if len(cells) > 1 else ""
        print(f"  成员姓名：{name or '（空！）'}")
        print(f"  电子签名：{'（空，待本人填写）' if not sign else sign}")
        if not name:
            failures.append("组员确认表里没有填成员姓名")
        break
    if not found_confirm:
        failures.append("找不到组员确认表")
        print("  ❌ 找不到组员确认表")

    # ---------- 汇总 ----------
    print()
    if failures:
        print(f"❌ 共 {len(failures)} 项不符：")
        for f in failures:
            print("   - " + f)
        return 1
    print("✅ 全部检查通过。")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
