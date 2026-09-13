# -*- coding: utf-8 -*-
"""
============================================================================
 文件：docs/tools/report_stack_fix.py
 用途：把实验报告 docx 里与技术栈相关的表述，从
       「Vue 3 + Spring Boot + MyBatis-Plus + 前后端分离三层结构」
       改写为
       「纯 Java 控制台程序 + 分层结构 + JDBC + MySQL」。

 为什么需要改：
   项目改用纯 Java 实现后，报告 4.1 总体设计、4.2 核心业务流程、2.2 需求描述
   中的技术栈表述与实际代码不一致。评分标准要求「设计与最终软件基本一致」，
   所以必须同步修改，否则属于报告与代码不符。

 实现要点（踩过的坑记录在这里）：
   1. 只在 XML 字符串层面替换 <w:t> 标签内的文字，**绝不重建段落**。
      早期版本按「段落重建」替换，把 docx 中 13 个空段落（行距占位）吞掉，
      段落数从 291 掉到 278，属于不该有的副作用。
   2. 原文字片段必须唯一，并按片段长度从长到短执行，避免短片段误命中。
   3. 每次执行前先从备份恢复，保证脚本可重复运行、结果稳定。
   4. 执行后自动比较段落数，不一致会给出警告。

 使用方法：
   python docs/tools/report_stack_fix.py     # 应用替换
   python docs/tools/verify_report.py        # 校验（建议每次执行后跑一遍）

 备份：实验一_..._实验报告模板_原始备份.docx（首次运行时自动生成）
============================================================================
"""
import re
import shutil
import zipfile
import os

DOCX = r"E:\SoftwareEngineeringHomework\UniversityManageSystem\docs\原始材料文本\实验一_基于工程意图的软件迭代开发_实验报告模板.docx"
BAK = DOCX.replace(".docx", "_原始备份.docx")

# ---------------------------------------------------------------------------
# 替换清单：(docx 中的原文字片段, 替换后的文字, 说明)
# 原文字片段必须与 docx 中 <w:t> 标签内的文字完全一致（含标点、空格）。
# ---------------------------------------------------------------------------
REPLACEMENTS = [
    # ===================== 4.1 总体设计（核心修改） =====================
    ("系统采用前后端分离的三层结构，主要由前端、后端和数据库三部分组成。",
     "系统采用控制台程序结构，由界面层（菜单交互）、业务逻辑层、数据访问层和数据库四部分组成。",
     "4.1 第1句：前后端分离三层 -> 控制台程序分层"),

    ("前端采用 Vue 3 构建，负责用户界面展示、路由控制、表单输入以及与后端接口的数据交互；"
     "使用 Vue Router 管理页面路由，Pinia 管理共享用户状态，Element Plus 提供基础界面组件，"
     "Axios 用于发送 HTTP 请求。",
     "界面层由 Java 控制台菜单实现，负责显示功能选项、接收键盘输入，把用户选择交给业务逻辑层处理，并输出执行结果。",
     "4.1 前端段：Vue 3 技术栈 -> 控制台菜单"),

    ("后端采用 Spring Boot，按照 Controller、Service、Mapper 等层次组织业务代码。"
     "Controller 负责接收和处理前端 HTTP 请求，Service 负责实现主要业务逻辑，"
     "Mapper 负责数据访问，MyBatis-Plus 用于数据库操作。",
     "业务逻辑层负责注册登录校验、活动信息维护、报名与取消报名等业务规则；"
     "数据访问层负责对数据库执行查询与更新操作。各层由普通 Java 类组成，通过方法调用协作，不引入任何框架。",
     "4.1 后端段：Spring Boot 分层 -> 业务逻辑层与数据访问层"),

    ("数据库采用 MySQL，主要保存用户、活动和活动报名记录等核心业务数据。",
     "数据库采用 MySQL，程序通过 JDBC 访问，主要保存用户、活动和活动报名记录等核心业务数据。",
     "4.1 数据库段：补充 JDBC 访问方式"),

    ("基本数据流：Vue 3 前端 → HTTP/JSON → Spring Boot Controller → Service → Mapper/MyBatis-Plus → MySQL。",
     "基本数据流：控制台菜单 → 业务逻辑层方法调用 → 数据访问层 → JDBC → MySQL。",
     "4.1 数据流：改写为控制台程序调用链"),

    # ===================== 4.2 核心业务流程 =====================
    ("学生登录系统 → 系统验证账号密码及身份",
     "学生登录系统 → 程序验证账号密码及身份",
     "4.2 流程：系统验证 -> 程序验证"),

    ("后端检查是否重复报名 → 未重复则创建报名记录 → 返回报名成功",
     "程序检查是否重复报名 → 未重复则创建报名记录 → 提示报名成功",
     "4.2 流程：后端检查/返回 -> 程序检查/提示"),

    ("如果活动不可报名或学生已经报名，则系统拒绝本次报名操作并返回相应提示。",
     "如果活动不可报名或学生已经报名，则程序拒绝本次报名操作并给出相应提示。",
     "4.2 尾句：系统拒绝/返回 -> 程序拒绝/提示"),

    # ===================== 2.2 需求描述 =====================
    ("系统应支持用户注册和登录，并根据用户身份进入对应功能范围。",
     "系统应支持用户注册和登录，并根据学生或教师身份进入对应的功能菜单。",
     "REQ-01：与「控制台菜单」实现一致"),

    ("学生应能够查看自己已经报名的活动及报名状态。",
     "学生应能够查看自己已经报名的活动及报名状态，并能够取消尚未参加的报名。",
     "REQ-04：补充取消报名能力"),

    # ===================== 占位符：统一标记为待填写 =====================
    ("【请描述校园活动管理系统V1.0所解决的主要业务问题，以及学生、活动组织教师等用户的核心目标。】",
     "【待填写】说明 V1.0 要解决的主要业务问题，以及学生、活动组织教师两类用户的核心目标。",
     "2.1 占位符标记"),

    ("【请说明1～3项具有代表性的需求取舍或范围控制决定。】",
     "【待填写】请说明 1~3 项具有代表性的需求取舍或范围控制决定及其理由。",
     "2.3 占位符标记"),

    ("【请记录2～4项关键设计决策，并说明理由。】",
     "【待填写】请记录 2~4 项关键设计决策（如身份与权限、重复报名规则、活动状态、数据校验、分层组织方式），并说明理由。",
     "4.4 占位符标记"),

    ("【开发完成后填写：根据最终实际实现情况概括 V1.0 功能，并插入关键界面截图。】",
     "【开发后填写】根据最终实际实现情况概括 V1.0 已完成的功能与运行方式，并按需插入少量关键运行截图（控制台操作过程截图）。",
     "6.1 占位符：界面截图 -> 控制台运行截图"),
]


def count_paragraphs(xml):
    """统计 <w:p> 数量，用于确认段落结构未被破坏。"""
    return len(re.findall(r"<w:p(?:\s[^>]*)?>", xml))


def replace_in_text_node(xml, old, new):
    """查找某个 <w:t> 节点并替换其中的文字，返回 (新 XML, 是否成功)。"""
    for m in re.finditer(r"<w:t[^>]*>(.*?)</w:t>", xml, re.S):
        content = m.group(1)
        if old in content:
            new_content = new if content.strip() == old.strip() else content.replace(old, new)
            return xml[:m.start(1)] + new_content + xml[m.end(1):], True
    return xml, False


def main():
    if not os.path.exists(BAK):
        shutil.copy2(DOCX, BAK)
        print("已创建备份:", os.path.basename(BAK))
    else:
        shutil.copy2(BAK, DOCX)
        print("已从备份恢复原始文件")

    zin = zipfile.ZipFile(DOCX, "r")
    items = zin.infolist()
    blobs = {i.filename: zin.read(i.filename) for i in items}
    zin.close()

    xml = blobs["word/document.xml"].decode("utf-8")
    before = count_paragraphs(xml)

    ok, fail = 0, []
    for old, new, reason in sorted(REPLACEMENTS, key=lambda x: len(x[0]), reverse=True):
        xml, done = replace_in_text_node(xml, old, new)
        if done:
            ok += 1
            print("[已替换] " + reason)
        else:
            fail.append(reason)

    after = count_paragraphs(xml)
    print("\n替换成功 %d 处，失败 %d 处" % (ok, len(fail)))
    if fail:
        print("未命中:", "、".join(fail))
    print("段落数：%d -> %d %s" % (before, after, "（一致，结构未破坏）" if before == after else "（不一致，请检查！）"))

    blobs["word/document.xml"] = xml.encode("utf-8")
    tmp = DOCX + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for i in items:
            zout.writestr(i, blobs[i.filename])
    os.replace(tmp, DOCX)
    print("已写回:", os.path.basename(DOCX))


if __name__ == "__main__":
    main()
