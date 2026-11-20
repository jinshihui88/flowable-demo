/* Flowable 请假审批 · 请求流程可视化实验室
 * 原生 JS，无框架、无构建。同源 fetch 调用 /api，逐条展示六个环节。
 *
 * 六个环节：1 触发操作 / 2 请求地址与方法 / 3 请求参数 /
 *          4 加载状态 / 5 响应结果 / 6 错误信息
 */
'use strict';

(function () {

  /* ==================== 小工具 ==================== */
  const $ = (id) => document.getElementById(id);

  // 只转义 & < >；引号保留，便于后续做 JSON 语法高亮（内容放在文本节点里）
  const esc = (s) => String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  const pad = (n) => String(n).padStart(2, '0');

  function nowTime() {
    const d = new Date();
    return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
      + '.' + String(d.getMilliseconds()).padStart(3, '0');
  }

  function newBusinessKey() {
    const d = new Date();
    return 'LR' + d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate())
      + '-' + pad(d.getHours()) + pad(d.getMinutes()) + pad(d.getSeconds())
      + '-' + Math.floor(Math.random() * 90 + 10);
  }

  const delay = (ms) => new Promise((r) => setTimeout(r, ms));

  // 教学用慢速模式：URL 带 ?slow=1 时把每次请求人为延后 1.2s，
  // 便于肉眼观察 / 截图留存「加载状态」这一环节；默认关闭，不影响真实调用。
  const SLOW_MODE = new URLSearchParams(window.location.search).get('slow') === '1';

  /** 轻量 JSON 语法高亮（先转义、再按 token 上色） */
  function highlight(value) {
    const raw = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    return esc(raw).replace(
      /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(?:true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
      (m) => {
        let cls = 'j-num';
        if (/^"/.test(m)) cls = /:\s*$/.test(m) ? 'j-key' : 'j-str';
        else if (/^(true|false)$/.test(m)) cls = 'j-bool';
        else if (m === 'null') cls = 'j-null';
        return '<span class="' + cls + '">' + m + '</span>';
      }
    );
  }

  const methodBadge = (m) => '<span class="method-pill m-' + m.toLowerCase() + '">' + m + '</span>';
  const chBadge = (m) => '<span class="ch-badge m-' + m.toLowerCase() + '">' + m + '</span>';

  /* ==================== 全局状态 ==================== */
  const state = {
    seq: 0, total: 0, ok: 0, err: 0, cost: 0,
    running: false,
    pid: '',        // 步骤 2 返回的 processInstanceId
    taskId: '',     // 步骤 3 返回的待办任务 ID
    stepStates: {}  // 步骤总览：key -> idle|loading|ok|err
  };

  function readCtx() {
    return {
      baseUrl: $('baseUrl').value.trim().replace(/\/+$/, ''),
      businessKey: $('businessKey').value.trim(),
      applicantId: $('applicantId').value.trim(),
      approverId: $('approverId').value.trim(),
      days: Number($('days').value),
      reason: $('reason').value.trim(),
      approved: $('approved').value === 'true',
      comment: $('comment').value.trim(),
      pid: state.pid,
      taskId: state.taskId
    };
  }

  function startBody(c) {
    return {
      businessKey: c.businessKey,
      applicantId: c.applicantId,
      approverId: c.approverId,
      days: c.days,
      reason: c.reason
    };
  }

  /* ==================== 7 个步骤定义（顺序即请求顺序） ==================== */
  const STEPS = [
    {
      key: 'definitions', name: '查看流程定义', desc: '确认 BPMN 已被自动部署',
      method: 'GET',
      trigger: '点击步骤卡片上的「执行此步骤」，或由「一键运行完整流程」自动触发',
      path: () => '/api/process/definitions',
      params: () => null,
      body: () => null,
      deps: '无',
      after: null
    },
    {
      key: 'start', name: '发起请假申请', desc: '写业务表 + 启动流程，返回流程实例 ID',
      method: 'POST',
      trigger: '点击「执行此步骤」，控制台里的 5 个字段原样作为 JSON 请求体',
      path: () => '/api/leave/start',
      params: (c) => startBody(c),
      body: (c) => startBody(c),
      deps: '无',
      after: (data) => {
        const pid = data && data.data && data.data.processInstanceId;
        if (pid) state.pid = pid;
      }
    },
    {
      key: 'tasks', name: '查询审批人待办', desc: '按 assignee 查出刚生成的审批任务',
      method: 'GET',
      trigger: '点击「执行此步骤」，用控制台里的审批人作为 userId 查询参数',
      path: (c) => '/api/leave/tasks?userId=' + encodeURIComponent(c.approverId),
      params: (c) => ({ userId: c.approverId }),
      body: () => null,
      deps: '步骤 2 启动的流程实例',
      after: (data, c) => {
        const list = (data && data.data) || [];
        const hit = list.find((t) => t.processInstanceId === state.pid) || list[0];
        if (hit && hit.taskId) state.taskId = hit.taskId;
      }
    },
    {
      key: 'complete', name: '办理审批任务', desc: '提交 approved，网关据此走通过/驳回分支',
      method: 'POST',
      trigger: '点击「执行此步骤」，taskId 来自步骤 3，审批结论取自控制台',
      path: (c) => '/api/leave/tasks/' + encodeURIComponent(c.taskId || 'NOT_SET') + '/complete',
      params: (c) => ({ operatorId: c.approverId, approved: c.approved, comment: c.comment }),
      body: (c) => ({ operatorId: c.approverId, approved: c.approved, comment: c.comment }),
      deps: '步骤 3 返回的 taskId',
      after: null
    },
    {
      key: 'status', name: '查询运行状态', desc: '流程是否已结束、发起人、起止时间',
      method: 'GET',
      trigger: '点击「执行此步骤」，processInstanceId 来自步骤 2',
      path: (c) => '/api/process/instances/' + encodeURIComponent(c.pid || 'NOT_SET') + '/status',
      params: (c) => ({ processInstanceId: c.pid || 'NOT_SET' }),
      body: () => null,
      deps: '步骤 2 返回的 processInstanceId',
      after: null
    },
    {
      key: 'historicTasks', name: '查询审批轨迹', desc: '历史任务：谁在什么时候办了什么',
      method: 'GET',
      trigger: '点击「执行此步骤」，查该流程实例的历史任务列表',
      path: (c) => '/api/process/instances/' + encodeURIComponent(c.pid || 'NOT_SET') + '/history/tasks',
      params: (c) => ({ processInstanceId: c.pid || 'NOT_SET' }),
      body: () => null,
      deps: '步骤 2 返回的 processInstanceId',
      after: null
    },
    {
      key: 'historicVars', name: '查询历史变量', desc: 'approved / 审批意见等最终落库的值',
      method: 'GET',
      trigger: '点击「执行此步骤」，核对 approved 与中文审批意见是否持久化',
      path: (c) => '/api/process/instances/' + encodeURIComponent(c.pid || 'NOT_SET') + '/history/variables',
      params: (c) => ({ processInstanceId: c.pid || 'NOT_SET' }),
      body: () => null,
      deps: '步骤 2 返回的 processInstanceId',
      after: null
    }
  ];

  /* ==================== 异常场景定义 ==================== */
  const SCENARIOS = {
    badDays: {
      name: '参数校验失败（days = 0）', desc: '故意传非法天数，观察 400 与字段级原因',
      method: 'POST',
      trigger: '点击「参数校验 400」按钮',
      path: () => '/api/leave/start',
      params: (c) => Object.assign(startBody(c), { days: 0 }),
      body: (c) => Object.assign(startBody(c), { days: 0 }),
      expect: 'HTTP 400 · days: 请假天数至少为 1'
    },
    emptyUser: {
      name: '空 userId（缺少查询参数值）', desc: '观察空参数被引擎拦下的 400',
      method: 'GET',
      trigger: '点击「空 userId 400」按钮',
      path: () => '/api/leave/tasks?userId=',
      params: () => ({ userId: '' }),
      body: () => null,
      expect: 'HTTP 400 · userId 不能为空'
    },
    unauthorized: {
      name: '越权办理不存在的任务', desc: '伪造 operatorId 与 taskId，观察 400',
      method: 'POST',
      trigger: '点击「越权办理 400」按钮',
      path: () => '/api/leave/tasks/NOT_A_REAL_TASK/complete',
      params: () => ({ operatorId: 'hacker', approved: true, comment: '伪造审批' }),
      body: () => ({ operatorId: 'hacker', approved: true, comment: '伪造审批' }),
      expect: 'HTTP 400 · 任务不存在、已办理或当前用户无权办理'
    },
    notFound: {
      name: '查询不存在的流程实例', desc: '观察业务异常如何被映射成 400 而不是 500',
      method: 'GET',
      trigger: '点击「实例不存在 400」按钮',
      path: () => '/api/process/instances/NOT_EXIST/status',
      params: () => ({ processInstanceId: 'NOT_EXIST' }),
      body: () => null,
      expect: 'HTTP 400 · 流程实例不存在：NOT_EXIST'
    }
  };

  /* ==================== 步骤总览渲染 ==================== */
  function renderStepper() {
    $('stepper').innerHTML = STEPS.map((s, i) => {
      const st = state.stepStates[s.key] || 'idle';
      const label = { idle: '待执行', loading: '请求中…', ok: '成功', err: '失败' }[st];
      const cost = state.stepStates[s.key + '_cost'] || '';
      return '<div class="step-node st-' + st + '" data-key="' + s.key + '" title="点击单独执行此步骤">'
        + '<div class="sn-top"><span class="sn-idx">' + (i + 1) + '</span>'
        + '<span class="sn-method m-' + s.method.toLowerCase() + '">' + s.method + '</span></div>'
        + '<div class="sn-name">' + s.name + '</div>'
        + '<div class="sn-state"><span class="dot"></span><span>' + label + '</span>'
        + '<span class="sn-cost">' + cost + '</span></div>'
        + '</div>';
    }).join('');

    $('stepper').querySelectorAll('.step-node').forEach((node) => {
      node.addEventListener('click', () => {
        if (state.running) return;
        const step = STEPS.find((s) => s.key === node.dataset.key);
        if (step) execute(step, step.key);
      });
    });
  }

  function setStepState(key, st, cost) {
    if (!key) return;
    state.stepStates[key] = st;
    if (cost != null) state.stepStates[key + '_cost'] = cost + ' ms';
    renderStepper();
  }

  /* ==================== 汇总条 ==================== */
  function renderSummary() {
    $('sumTotal').textContent = state.total;
    $('sumOk').textContent = state.ok;
    $('sumErr').textContent = state.err;
    $('sumCost').textContent = state.cost + ' ms';
    if (state.total === 0) {
      $('sumTip').textContent = '尚未发起请求，先点「一键运行完整流程」试试';
    } else if (state.running) {
      $('sumTip').textContent = '流程执行中，请稍候…';
    } else {
      $('sumTip').textContent = state.err === 0
        ? '本次全部请求成功，流程已闭环'
        : '有 ' + state.err + ' 次请求返回错误，见红色卡片';
    }
  }

  /* ==================== 请求卡片 ==================== */
  function appendCard(meta, url, params, stepKey, isScenario) {
    state.seq += 1;
    const seq = state.seq;
    $('flowEmpty').style.display = 'none';

    const paramsHtml = params == null
      ? '<span class="code-empty">本次请求无请求体、无查询参数</span>'
      : '<pre class="code">' + highlight(params) + '</pre>';

    const el = document.createElement('article');
    el.className = 'req-card c-loading';
    el.id = 'card-' + seq;
    el.dataset.seq = seq;
    el.innerHTML =
      '<div class="card-head">'
      + chBadge(meta.method)
      + '<span class="ch-title">' + esc(meta.name) + '</span>'
      + '<span class="ch-hint">' + esc(meta.desc || '') + '</span>'
      + '<span class="ch-time">' + nowTime() + '</span>'
      + '<span class="ch-status s-loading" data-role="status">请求中</span>'
      + '</div>'
      + '<div class="card-body">'

      + '<div class="sec"><div class="sec-label"><i>1</i>触发操作</div><div class="sec-content">'
      + '<p class="trigger-note">' + esc(meta.trigger || '点击按钮发起请求') + '</p>'
      + '<button class="btn-mini" data-role="rerun">执行此步骤</button>'
      + (isScenario ? '<span class="muted" style="margin-left:10px">预期：' + esc(meta.expect || '') + '</span>' : '')
      + '</div></div>'

      + '<div class="sec"><div class="sec-label"><i>2</i>请求地址与方法</div><div class="sec-content">'
      + '<div class="url-line">' + methodBadge(meta.method)
      + '<span class="url-text">' + esc(url) + '</span></div>'
      + (meta.deps ? '<div class="muted" style="margin-top:5px">依赖数据：' + esc(meta.deps) + '</div>' : '')
      + '</div></div>'

      + '<div class="sec"><div class="sec-label"><i>3</i>请求参数</div><div class="sec-content">' + paramsHtml + '</div></div>'

      + '<div class="sec"><div class="sec-label"><i>4</i>加载状态</div><div class="sec-content" data-role="load">'
      + '<div class="load-box"><span class="spinner"></span><span class="load-track"><span class="load-bar"></span></span>'
      + '<span class="load-text">请求中…</span></div></div></div>'

      + '<div class="sec"><div class="sec-label"><i>5</i>响应结果</div><div class="sec-content" data-role="resp">'
      + '<span class="code-empty">等待响应…</span></div></div>'

      + '<div class="sec"><div class="sec-label"><i>6</i>错误信息</div><div class="sec-content" data-role="err">'
      + '<span class="code-empty">等待请求完成…</span></div></div>'

      + '</div>';

    $('flow').appendChild(el);

    const refs = {
      el: el,
      status: el.querySelector('[data-role="status"]'),
      load: el.querySelector('[data-role="load"]'),
      resp: el.querySelector('[data-role="resp"]'),
      err: el.querySelector('[data-role="err"]'),
      rerun: el.querySelector('[data-role="rerun"]')
    };
    refs.rerun.disabled = true;
    refs.rerun.addEventListener('click', () => {
      if (!state.running) execute(meta, stepKey, isScenario);
    });
    return refs;
  }

  function finishCard(refs, stepKey, result) {
    const ok = result.ok;
    refs.el.classList.remove('c-loading');
    refs.el.classList.add(ok ? 'c-ok' : 'c-err');
    refs.status.className = 'ch-status ' + (ok ? 's-ok' : 's-err');
    refs.status.textContent = (ok ? '成功 ' : '失败 ') + result.status;
    refs.rerun.disabled = false;

    // 环节 4：加载状态
    refs.load.innerHTML = '<span class="done-text' + (ok ? '' : ' err') + '">'
      + (ok ? '✓ 已完成' : '✗ 已完成（返回 ' + result.status + '）')
      + ' · 耗时 ' + result.ms + ' ms</span>';

    // 环节 5：响应结果
    refs.resp.innerHTML = '<div style="margin-bottom:7px"><span class="ch-status '
      + (ok ? 's-ok' : 's-err') + '">HTTP ' + result.status + ' ' + esc(result.statusText)
      + '</span> <span class="muted" style="margin-left:8px">Content-Type: application/json</span></div>'
      + '<pre class="code">' + highlight(result.body) + '</pre>';

    // 环节 6：错误信息
    if (ok) {
      refs.err.innerHTML = '<span class="code-empty">无 · 本次请求成功返回（code = '
        + esc(String(result.body && result.body.code != null ? result.body.code : 0)) + '）</span>';
    } else {
      const msg = (result.body && result.body.message)
        ? result.body.message
        : (typeof result.body === 'string' ? result.body : '无响应体');
      refs.err.innerHTML = '<div class="err-box">'
        + '<div class="err-title">✗ 请求失败 · HTTP ' + result.status + ' ' + esc(result.statusText) + '</div>'
        + '<div class="err-msg">' + esc(msg) + '</div>'
        + '<div class="err-hint">错误来自统一响应体 ApiResult.message，由 GlobalExceptionHandler 返回</div>'
        + '</div>';
    }

    setStepState(stepKey, ok ? 'ok' : 'err', result.ms);
  }

  function networkErrorCard(refs, stepKey, error, ms) {
    refs.el.classList.remove('c-loading');
    refs.el.classList.add('c-err');
    refs.status.className = 'ch-status s-err';
    refs.status.textContent = '网络错误';
    refs.rerun.disabled = false;
    refs.load.innerHTML = '<span class="done-text err">✗ 请求未完成 · 耗时 ' + ms + ' ms</span>';
    refs.resp.innerHTML = '<span class="code-empty">无响应（请求未到达服务端）</span>';
    refs.err.innerHTML = '<div class="err-box">'
      + '<div class="err-title">✗ 网络层错误 · fetch 抛出异常</div>'
      + '<div class="err-msg">' + esc(error && error.message ? error.message : String(error)) + '</div>'
      + '<div class="err-hint">请检查接口基址、应用是否已启动、以及浏览器控制台</div>'
      + '</div>';
    setStepState(stepKey, 'err', ms);
  }

  /* ==================== 核心：发起一次请求 ==================== */
  async function execute(meta, stepKey, isScenario) {
    const c = readCtx();
    const path = meta.path(c);
    const url = c.baseUrl + path;
    const params = meta.params ? meta.params(c) : null;
    const body = meta.body ? meta.body(c) : null;

    state.total += 1;
    state.running = true;
    renderSummary();
    setStepState(stepKey, 'loading');

    const refs = appendCard(meta, url, params, stepKey, isScenario);
    if (SLOW_MODE) await delay(1200);   // 仅慢速模式：拉长等待，便于观察加载态
    const t0 = performance.now();
    let result;

    try {
      const init = { method: meta.method, headers: {} };
      if (body != null) {
        init.headers['Content-Type'] = 'application/json; charset=utf-8';
        init.body = JSON.stringify(body);
      }
      const res = await fetch(url, init);
      const text = await res.text();
      let parsed;
      try { parsed = JSON.parse(text); } catch (_) { parsed = text; }
      const ms = Math.round(performance.now() - t0);
      result = {
        ok: res.ok, status: res.status, statusText: res.statusText || '',
        body: parsed, ms: ms
      };
      finishCard(refs, stepKey, result);
      if (res.ok && meta.after) {
        try { meta.after(parsed, c); } catch (_) { /* 联动解析失败不影响主流程 */ }
      }
    } catch (e) {
      const ms = Math.round(performance.now() - t0);
      result = { ok: false, ms: ms, network: true };
      networkErrorCard(refs, stepKey, e, ms);
    }

    state.cost += result.ms;
    if (result.ok) state.ok += 1; else state.err += 1;
    state.running = false;
    renderSummary();
    return result;
  }

  /* ==================== 一键运行完整流程 ==================== */
  async function runAll() {
    if (state.running) return;
    // 每次运行换一个业务单号，避免与上一次运行的幂等键冲突
    $('businessKey').value = newBusinessKey();
    state.pid = '';
    state.taskId = '';
    state.stepStates = {};
    renderStepper();

    for (const step of STEPS) {
      await execute(step, step.key, false);
      await delay(180);   // 让每一步的状态变化肉眼可辨，也便于逐步截图
    }
  }

  /* ==================== 重置 ==================== */
  function reset() {
    if (state.running) return;
    state.seq = 0; state.total = 0; state.ok = 0; state.err = 0; state.cost = 0;
    state.pid = ''; state.taskId = ''; state.stepStates = {};
    $('flow').innerHTML = '<div class="flow-empty" id="flowEmpty">'
      + '<div class="empty-icon">↧</div><p>这里还没有请求</p>'
      + '<p class="empty-sub">点击上方「一键运行完整流程」，或单独执行某个步骤</p></div>';
    $('businessKey').value = newBusinessKey();
    renderStepper();
    renderSummary();
  }

  /* ==================== 异常场景 ==================== */
  async function runScenario(kind) {
    if (state.running) return;

    if (kind === 'duplicate') {
      // 连发两次相同 businessKey：第一次成功，第二次命中唯一索引返回 409
      const biz = 'LR-DUP-' + Math.floor(Math.random() * 9000 + 1000);
      $('businessKey').value = biz;
      const meta = Object.assign({}, STEPS[1], {
        name: '重复提交同一业务单号（第 2 次）',
        desc: '相同 businessKey 再次发起，命中唯一索引 uk_business_key',
        trigger: '点击「重复单号 409」按钮：页面自动连续发起两次 /api/leave/start',
        expect: 'HTTP 409 · 该业务单号已存在，请勿重复提交'
      });
      await execute(STEPS[1], 'start', false);
      await delay(180);
      await execute(meta, 'start', true);
      return;
    }

    const s = SCENARIOS[kind];
    if (!s) return;
    await execute(s, null, true);
  }

  /* ==================== 初始化 ==================== */
  function init() {
    $('baseUrl').value = window.location.origin;
    $('businessKey').value = newBusinessKey();
    $('applicantId').value = 'user001';
    $('approverId').value = 'manager001';
    $('days').value = '3';
    $('reason').value = '家庭事务';
    $('comment').value = '同意，请按计划安排工作交接。';

    $('btnRunAll').addEventListener('click', runAll);
    $('btnReset').addEventListener('click', reset);
    document.querySelectorAll('[data-scenario]').forEach((btn) => {
      btn.addEventListener('click', () => runScenario(btn.dataset.scenario));
    });

    renderStepper();
    renderSummary();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
