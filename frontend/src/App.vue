<template>
  <div class="app-root">
    <div class="app-bg-layer" aria-hidden="true" />
    <ThemeSwitcher />
    <div class="app-main">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import ThemeSwitcher from './components/ThemeSwitcher.vue'
</script>
