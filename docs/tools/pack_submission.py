# -*- coding: utf-8 -*-
"""
文件：docs/tools/pack_submission.py
用途：把 V2.0 的提交内容打成一个 zip（和 V1.0 的提交包结构保持一致）。

为什么写成脚本而不是手工复制：
  提交包必须"打得干净"—— 少一个文件、多一个 node_modules、报告忘了放进去，
  都是交作业前容易漏的事。写成脚本之后，内容清单一目了然，
  而且每次重打的结果完全一致；改完代码重跑一条命令就行。

打包结构（与 校园活动管理系统_V1.0_提交包.zip 对齐）：
  校园活动管理系统V2.0/
  ├── 提交说明.txt              （V2.0 新增：告诉老师先看哪里）
  ├── build.bat                编译与运行脚本
  ├── README.md                工程说明
  ├── config/db.properties.example
  ├── db/schema.sql            建库建表 + 演示数据
  ├── docs/
  │   ├── 实验二_实验报告_校园活动管理系统V2.0.docx
  │   ├── 版本升级路线图.md
  │   └── 作业2/*.md           需求过程记录（访谈、需求、基线、测试用例、任务清单）
  ├── frontend/                Vue 3 前端源码（排除 node_modules / dist）
  ├── lib/*.jar                MySQL 驱动 + Gson
  └── src/com/hbk/activity/**  Java 源码

明确**不打包**的东西：
  - src/Main.java          IntelliJ 新建项目留下的 Hello World 模板，与本项目无关
                           （V1.0 的包里误带了，V2.0 去掉）
  - frontend/node_modules  依赖，几百 MB，npm install 即可恢复
  - frontend/dist          构建产物
  - docs/作业2/新建文件夹 (10)/   老师给的课程材料，含 116MB 的访谈 exe
  - docs/tools/*.py        开发期脚本，不属于交付物
  - docs/原始材料文本/        实验一的课程材料
  - 任何 .git / build / .idea

用法：
  python docs/tools/pack_submission.py
输出：
  校园活动管理系统_V2.0_提交包.zip（仓库根目录，已加入 .gitignore）
"""
import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

VERSION = "V2.0"
PKG_NAME = f"校园活动管理系统{VERSION}"
STAGE = os.path.join(ROOT, "build", "submit", PKG_NAME)
ZIP_PATH = os.path.join(ROOT, f"校园活动管理系统_{VERSION}_提交包.zip")

# 要放进包里的单个文件：(源相对路径, 包内相对路径)
FILES = [
    ("build.bat", "build.bat"),
    ("README.md", "README.md"),
    (os.path.join("config", "db.properties.example"), os.path.join("config", "db.properties.example")),
    (os.path.join("db", "schema.sql"), os.path.join("db", "schema.sql")),
    # 报告：填写后的模板，在包里改成规范的文件名
    (os.path.join("docs", "作业2", "新建文件夹 (10)", "实验二_V2.0_实验报告模板.docx"),
     os.path.join("docs", f"实验二_实验报告_校园活动管理系统{VERSION}.docx")),
    (os.path.join("docs", "版本升级路线图.md"), os.path.join("docs", "版本升级路线图.md")),
]

# 要整目录复制的：(源相对路径, 包内相对路径, 排除的目录名集合, 排除的文件名集合)
DIRS = [
    ("src", "src", set(), {"Main.java"}),          # 排除 IDE 模板文件
    ("lib", "lib", set(), set()),
    ("frontend", "frontend", {"node_modules", "dist", ".npm-cache"}, set()),
    (os.path.join("docs", "作业2"), os.path.join("docs", "作业2"),
     {"新建文件夹 (10)"}, set()),                   # 排除老师给的课程材料
]

# docs/作业2 下只收 .md（过程记录），不收别的
DOC_MD_ONLY = True


def iter_dir(src_root, exclude_dirs, exclude_files, md_only=False):
    """遍历目录，返回 [(绝对路径, 相对路径)]。"""
    out = []
    for dirpath, dirnames, filenames in os.walk(src_root):
        # 就地修改 dirnames 才能真的阻止 os.walk 进入这些目录
        dirnames[:] = [d for d in dirnames if d not in exclude_dirs]
        for fn in filenames:
            if fn in exclude_files:
                continue
            if md_only and not fn.lower().endswith(".md"):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, src_root)
            out.append((full, rel))
    return out


SUBMIT_NOTE = """\
校园活动管理系统 V2.0 —— 提交说明
================================================

一、先看哪里
  docs/实验二_实验报告_校园活动管理系统V2.0.docx   ← 实验二实验报告（本次主要交付物）
  docs/版本升级路线图.md                          ← 各版本的升级目标与技术方案
  docs/作业2/                                     ← 需求过程记录
      访谈记录.md        三个角色三轮访谈的逐字记录与关键发现
      需求提取.md        18 条候选需求、角色权限边界、冲突核对、8 条最小假设
      V2.0需求基线.md    16 条用户故事与验收标准
      V2.0测试用例.md    测试用例表（TC-01 ~ TC-77）
      V2.0开发任务清单.md 各阶段的实现说明

二、怎么跑起来
  1. 启动 MySQL，并执行建库脚本：
       Get-Content db\\schema.sql -Encoding utf8 | & mysql.exe -u root -p
  2. 复制数据库配置模板并填入自己的密码：
       copy config\\db.properties.example config\\db.properties
  3. 前端（浏览器界面）：
       终端1： build.bat web            启动后端接口服务（8080）
       终端2： cd frontend
               npm install             首次需要
               npm run dev             启动前端（5173）
       浏览器打开 http://localhost:5173
  4. 控制台版本（保留的 V1.0 入口）：
       build.bat

三、演示账号（密码均为 123456）
  teacher01    教师
  student01    学生
  student02    学生
  student04    学生
  admin01      系统管理员
  student03    学生（已停用，用来验证"停用账号不能登录"）

四、怎么跑测试
  build.bat test-all              跑全部 9 个测试类（需要 MySQL 已启动）
  build.bat run com.hbk.activity.tool.ApiV2SmokeTest
                                  接口层测试（需要先另开终端跑 build.bat web）

五、V2.0 相对 V1.0 的主要变化
  1. 报名由"一次性动作"改为带审核与候补的状态流程，共 5 种状态；
  2. 活动可由负责教师设置人数上限与参加条件；
  3. 新增报名审核与候补递补（候补按"进入候补的时间"排序）；
  4. 新增系统管理员角色（管账号、监督活动，但不介入具体报名）；
  5. 账号新增状态，被停用的账号不能登录；
  6. 接口从 14 个扩展到 21 个，前端页面从 7 个增加到 9 个。

六、技术栈
  JDK 17+、MySQL 8.x、Vue 3 + Vite + Vue Router + Pinia + Element Plus + Axios。
  后端接口层用 JDK 自带的 com.sun.net.httpserver.HttpServer 手写
  （不依赖任何 Web 框架），依赖只有 lib/ 下的两个 jar。
"""


def main():
    if not os.path.exists(os.path.dirname(STAGE)):
        os.makedirs(os.path.dirname(STAGE))
    if os.path.exists(STAGE):
        shutil.rmtree(STAGE)
    os.makedirs(STAGE)

    copied = []
    missing = []

    # ---------- 单个文件 ----------
    for src_rel, dst_rel in FILES:
        src = os.path.join(ROOT, src_rel)
        if not os.path.exists(src):
            missing.append(src_rel)
            continue
        dst = os.path.join(STAGE, dst_rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        copied.append((dst_rel, os.path.getsize(dst)))

    # ---------- 目录 ----------
    for src_rel, dst_rel, ex_dirs, ex_files in DIRS:
        src_root = os.path.join(ROOT, src_rel)
        if not os.path.isdir(src_root):
            missing.append(src_rel)
            continue
        md_only = DOC_MD_ONLY and src_rel.replace("\\", "/").endswith("docs/作业2")
        for full, rel in iter_dir(src_root, ex_dirs, ex_files, md_only):
            dst = os.path.join(STAGE, dst_rel, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(full, dst)
            copied.append((os.path.join(dst_rel, rel), os.path.getsize(dst)))

    # ---------- 提交说明 ----------
    note = os.path.join(STAGE, "提交说明.txt")
    # 用 UTF-8 BOM 写：老版本记事本打开也不会乱码
    with open(note, "w", encoding="utf-8-sig", newline="\r\n") as f:
        f.write(SUBMIT_NOTE)
    copied.append(("提交说明.txt", os.path.getsize(note)))

    # ---------- 打 zip ----------
    if os.path.exists(ZIP_PATH):
        os.remove(ZIP_PATH)
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, dirnames, filenames in os.walk(STAGE):
            for fn in sorted(filenames):
                full = os.path.join(dirpath, fn)
                arc = os.path.join(PKG_NAME, os.path.relpath(full, STAGE))
                z.write(full, arc)

    # ---------- 报告 ----------
    print(f"打包目录：{os.path.relpath(STAGE, ROOT)}")
    print(f"文件总数：{len(copied)}（不含目录）\n")
    by_top = {}
    for rel, size in copied:
        top = rel.split(os.sep)[0]
        d = by_top.setdefault(top, [0, 0])
        d[0] += 1
        d[1] += size
    print("各部分统计：")
    for top in sorted(by_top):
        n, s = by_top[top]
        print(f"  {top:<22} {n:>4} 个文件  {s/1024:>9,.1f} KB")
    total = sum(s for _, s in copied)
    print(f"  {'合计':<22} {len(copied):>4} 个文件  {total/1024:>9,.1f} KB")

    if missing:
        print("\n⚠️ 以下内容没找到，未打进包里：")
        for m in missing:
            print("   - " + m)

    print(f"\n已生成提交包：{os.path.relpath(ZIP_PATH, ROOT)}"
          f"（{os.path.getsize(ZIP_PATH)/1024/1024:.2f} MB）")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
