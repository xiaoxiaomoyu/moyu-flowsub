<template>
  <main class="app-shell secondary-page">
    <header class="topbar">
      <div>
        <h1>会后总结</h1>
        <p>当前阶段先完成实时翻译与上下文修正，会后总结将在后续归档流程中生成。</p>
      </div>
      <nav class="topnav">
        <RouterLink to="/">返回实时同传</RouterLink>
      </nav>
    </header>

    <section class="panel summary-placeholder">
      <h2>后续能力预留</h2>
      <p>这里将展示中文摘要、时间线、术语表、重点句和七牛云归档资源。</p>
      <div class="summary-grid">
        <div>中文摘要</div>
        <div>时间线</div>
        <div>术语表</div>
        <div>云端归档</div>
      </div>
    </section>
  </main>
</template>
