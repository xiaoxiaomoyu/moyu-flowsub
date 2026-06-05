<template>
  <main class="app-shell secondary-page">
    <header class="topbar">
      <div>
        <h1>会后总结</h1>
        <p>第一阶段保留页面入口，真实总结将在后续接入 DeepSeek-V4-Pro 后生成。</p>
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
