<template>
  <el-dialog
    :model-value="visible"
    title="选择本段交通方式"
    width="480px"
    destroy-on-close
    @close="emit('cancel')"
  >
    <div class="travel-mode-dialog">
      <p class="dialog-copy">
        {{ routeSummary }}
      </p>
      <p class="dialog-tip">手动选择只影响当前这一段、当前这一次。</p>

      <div class="mode-grid" role="radiogroup" aria-label="本段交通方式">
        <button
          v-for="item in modeOptions"
          :key="item.code"
          type="button"
          class="mode-option"
          :class="{ active: selectedMode === item.code }"
          @click="emit('select', item.code)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('cancel')">取消</el-button>
        <el-button type="primary" :disabled="!selectedMode" @click="emit('confirm')">按所选方式重算</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedMode: { type: String, default: '' },
  fromName: { type: String, default: '' },
  toName: { type: String, default: '' }
})

const emit = defineEmits(['select', 'confirm', 'cancel'])

const modeOptions = [
  { code: 'walk', label: '步行' },
  { code: 'bike', label: '骑行' },
  { code: 'transit', label: '公交' },
  { code: 'taxi', label: '打车' }
]

const routeSummary = computed(() => {
  const from = props.fromName?.trim() || '当前位置'
  const to = props.toName?.trim() || '当前站点'
  return `从 ${from} 到 ${to}，请选择这一次要查看的交通方式。`
})
</script>

<style scoped>
.travel-mode-dialog {
  display: grid;
  gap: 14px;
}

.dialog-copy,
.dialog-tip {
  margin: 0;
  line-height: 1.7;
}

.dialog-copy {
  color: #1f2d3d;
}

.dialog-tip {
  color: #6b7b8d;
  font-size: 13px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mode-option {
  min-height: 48px;
  border-radius: 16px;
  border: 1px solid rgba(188, 214, 255, 0.9);
  background: #f8fbff;
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-option:hover {
  border-color: rgba(64, 158, 255, 0.85);
  transform: translateY(-1px);
}

.mode-option.active {
  border-color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  color: #2d79c7;
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.14);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 520px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .dialog-footer {
    flex-direction: column-reverse;
  }
}
</style>
