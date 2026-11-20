# -*- coding: utf-8 -*-
"""
flowable-demo 请求流程可视化页面：按真实操作流程逐步截图。

前置：应用已在 http://localhost:8080 启动（java -jar target/flowable-demo-1.0.0.jar）。
运行：python tools/capture_flow.py
产出：docs/images/*.png，供 docs/请求流程可视化页面操作指南.md 引用。

截图全部来自真实点击 + 真实接口调用；只有「加载状态」用页面自带的
?slow=1 教学慢速模式拉长等待，便于捕捉请求中的界面态。
"""
import os
import time
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
OUT = r"D:/project/flowable-demo/docs/images"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

os.makedirs(OUT, exist_ok=True)


def shot(locator_or_page, name, full=False):
    path = os.path.join(OUT, name)
    if full:
        locator_or_page.screenshot(path=path, full_page=True)
    else:
        locator_or_page.screenshot(path=path)
    size = os.path.getsize(path)
    print("  [SHOT] %-38s %7.1f KB" % (name, size / 1024))


def wait_done(page, seq, ok=True, timeout=20000):
    cls = "s-ok" if ok else "s-err"
    page.locator("#card-%d [data-role='status'].%s" % (seq, cls)).wait_for(timeout=timeout)


def wait_last_done(page, ok=False, timeout=20000):
    cls = "s-ok" if ok else "s-err"
    page.locator(".req-card [data-role='status'].%s" % cls).last.wait_for(timeout=timeout)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME,
            headless=True,
            args=["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
        )
        ctx = browser.new_context(
            viewport={"width": 1440, "height": 1000},
            device_scale_factor=1.25,
            locale="zh-CN",
        )
        page = ctx.new_page()
        js_errors = []
        page.on("pageerror", lambda e: js_errors.append(str(e)))

        # 截图前把 manager001 的历史遗留待办办理掉，保证步骤 3 只返回本次流程的任务
        try:
            import json as _json
            resp = page.request.get(BASE + "/api/leave/tasks?userId=manager001")
            leftovers = resp.json().get("data") or []
            for t in leftovers:
                page.request.post(
                    BASE + "/api/leave/tasks/%s/complete" % t["taskId"],
                    data=_json.dumps({"operatorId": "manager001", "approved": True,
                                      "comment": "截图前清理遗留待办"}, ensure_ascii=False),
                    headers={"Content-Type": "application/json; charset=utf-8"},
                )
            print("A0. 已清理遗留待办 %d 条" % len(leftovers))
        except Exception as e:
            print("A0. 清理遗留待办失败（不影响截图）:", e)

        # ---------- A. 加载状态（教学慢速模式） ----------
        print("A. 捕捉加载状态")
        page.goto(BASE + "/?slow=1", wait_until="networkidle")
        page.locator('.step-node[data-key="definitions"]').click()
        page.locator("#card-1 [data-role='status'].s-loading").wait_for(timeout=10000)
        page.wait_for_timeout(250)
        shot(page.locator("#card-1"), "02-loading-state.png")
        page.locator("#card-1 [data-role='status'].s-ok").wait_for(timeout=20000)

        # ---------- B. 页面初始态 ----------
        print("B. 页面初始态")
        page.goto(BASE + "/", wait_until="networkidle")
        page.wait_for_timeout(200)
        shot(page, "01-page-init.png", full=True)

        # ---------- C. 单步执行 1~4 ----------
        print("C. 单步执行 1~7")
        plan = [
            ("definitions",   1, "03-step1-definitions.png"),
            ("start",         2, "04-step2-start.png"),
            ("tasks",         3, "05-step3-tasks.png"),
            ("complete",      4, "06-step4-complete.png"),
            ("status",        5, "07-step5-status.png"),
            ("historicTasks", 6, "08-step6-history-tasks.png"),
            ("historicVars",  7, "09-step7-history-vars.png"),
        ]
        for key, seq, name in plan:
            page.locator('.step-node[data-key="%s"]' % key).click()
            wait_done(page, seq, ok=True)
            shot(page.locator("#card-%d" % seq), name)

        # ---------- D. 一键运行完整流程 ----------
        print("D. 一键运行完整流程")
        page.locator("#btnReset").click()
        page.locator("#flowEmpty").wait_for(timeout=5000)
        page.locator("#btnRunAll").click()
        page.wait_for_function(
            "() => document.querySelectorAll('.req-card').length === 7 "
            "&& !document.querySelector('.req-card.c-loading')",
            timeout=60000,
        )
        page.wait_for_timeout(300)
        # 步骤总览：7 个节点的状态 + 汇总
        shot(page.locator(".panel").nth(1), "10-runall-stepper.png")
        # 完整请求流水长图
        shot(page.locator("#flow"), "11-runall-flow.png")

        # ---------- E. 错误场景 ----------
        print("E. 错误场景")
        page.locator('[data-scenario="badDays"]').click()
        page.wait_for_function(
            "() => !document.querySelector('.req-card.c-loading') "
            "&& document.querySelectorAll('.req-card.c-err').length === 1",
            timeout=20000,
        )
        shot(page.locator(".req-card").last, "12-error-400-validation.png")

        page.locator('[data-scenario="duplicate"]').click()
        page.wait_for_function(
            "() => !document.querySelector('.req-card.c-loading') "
            "&& document.querySelectorAll('.req-card.c-err').length === 2",
            timeout=30000,
        )
        shot(page.locator(".req-card").last, "13-error-409-duplicate.png")

        # ---------- F. 网络层错误 ----------
        print("F. 网络层错误")
        page.locator("#baseUrl").fill("http://127.0.0.1:9")
        page.locator('.step-node[data-key="definitions"]').click()
        page.wait_for_function(
            "() => !document.querySelector('.req-card.c-loading') "
            "&& document.querySelectorAll('.req-card.c-err').length === 3",
            timeout=20000,
        )
        shot(page.locator(".req-card").last, "14-error-network.png")

        # ---------- 校验 ----------
        print("\n=== 页面 JS 异常 ===")
        print(js_errors if js_errors else "无（pageerror = 0）")
        print("=== 截图数量 ===", len([f for f in os.listdir(OUT) if f.endswith(".png")]))

        browser.close()


if __name__ == "__main__":
    t0 = time.time()
    main()
    print("完成，用时 %.1fs" % (time.time() - t0))
