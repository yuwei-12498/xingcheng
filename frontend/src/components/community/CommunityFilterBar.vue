<template>
  <section class="filter-shell">
    <div class="filter-intro">
      <p class="filter-kicker">BROWSE FILTERS</p>
    </div>

    <div class="filter-grid">
      <div class="filter-block">
        <span class="filter-label">排序</span>
        <div class="filter-row sort-group">
          <button
            v-for="item in sortOptions"
            :key="item.value"
            type="button"
            class="sort-pill"
            :class="{ active: modelSort === item.value }"
            @click="$emit('update:sort', item.value); $emit('search')"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="filter-block">
        <span class="filter-label">关键词</span>
        <div class="filter-row search-group">
          <el-input
            :model-value="modelKeyword"
            placeholder="搜索标题、分享语或标签"
            clearable
            @update:model-value="$emit('update:keyword', $event)"
            @keyup.enter="$emit('search')"
          />
          <el-button round class="search-btn" @click="$emit('search')">筛选</el-button>
        </div>
      </div>

      <div class="filter-block">
        <span class="filter-label">主题</span>
        <div class="filter-row theme-group">
          <button
            type="button"
            class="theme-pill"
            :class="{ active: !modelTheme }"
            @click="$emit('update:theme', ''); $emit('search')"
          >
            全部
          </button>
          <button
            v-for="item in themes"
            :key="item"
            type="button"
            class="theme-pill"
            :class="{ active: modelTheme === item }"
            @click="$emit('update:theme', item); $emit('search')"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  modelSort: {
    type: String,
    default: 'latest'
  },
  modelKeyword: {
    type: String,
    default: ''
  },
  modelTheme: {
    type: String,
    default: ''
  },
  themes: {
    type: Array,
    default: () => []
  }
})

defineEmits(['update:sort', 'update:keyword', 'update:theme', 'search'])

const sortOptions = [
  { label: '最新优先', value: 'latest' },
  { label: '热度优先', value: 'hot' }
]
</script>

<style scoped>
.filter-shell {
  display: grid;
  gap: 20px;
  margin-top: 0;
  padding: 24px 26px;
  border-radius: var(--radius-panel);
  border: 1px solid rgba(188, 214, 255, 0.84);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 250, 255, 0.9));
  box-shadow: var(--shadow-soft);
}

.filter-intro {
  display: grid;
  gap: 8px;
}

.filter-kicker {
  margin: 0;
  color: var(--brand-600);
  letter-spacing: 0.18em;
  font-size: 12px;
}

.filter-grid,
.filter-block {
  display: grid;
}

.filter-grid {
  gap: 18px;
}

.filter-block {
  gap: 12px;
}

.filter-label {
  color: var(--text-soft);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.filter-row,
.sort-group,
.theme-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.search-group {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.sort-pill,
.theme-pill {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(188, 214, 255, 0.84);
  background: rgba(246, 250, 255, 0.96);
  color: var(--text-body);
  cursor: pointer;
  transition: all 0.2s ease;
}

.sort-pill.active,
.theme-pill.active {
  border-color: transparent;
  background: linear-gradient(135deg, var(--brand-500), #81bbff);
  color: #fff;
  box-shadow: 0 10px 24px rgba(95, 158, 255, 0.18);
}

.search-btn {
  min-height: 40px;
  padding: 0 18px;
  border-color: rgba(188, 214, 255, 0.84);
  background: rgba(255, 255, 255, 0.92);
  color: var(--text-strong);
  box-shadow: 0 8px 18px rgba(95, 158, 255, 0.08);
}

:deep(.el-input__wrapper) {
  border-radius: 18px;
  box-shadow: 0 0 0 1px rgba(188, 214, 255, 0.84) inset;
  background: rgba(248, 252, 255, 0.96);
}

@media (max-width: 800px) {
  .search-group {
    grid-template-columns: 1fr;
  }
}
</style>
