<template>
  <header class="page-header">
    <div class="page-header-text">
      <h1 class="page-title">
        <slot name="title">{{ title }}</slot>
      </h1>
      <p v-if="description || $slots.description" class="page-description">
        <slot name="description">{{ description }}</slot>
      </p>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
  /* 不设 margin，由 .page-content > * + * 统一控制 16px 元素间距 */
}
.page-header-text {
  flex: 1;
  min-width: 0;
}
.page-title {
  font-size: var(--text-2xl);
  font-weight: var(--weight-semi);
  color: var(--text-primary);
  letter-spacing: -0.015em;
  line-height: 1.3;
}
.page-description {
  margin-top: var(--space-1);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: var(--leading-normal);
}
.page-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>