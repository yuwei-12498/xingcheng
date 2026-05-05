<template>
  <div v-if="loggedIn" class="composer-shell">
    <div v-if="replyingTo" class="reply-banner">
      <span>正在回复 {{ replyingTo.authorLabel }}</span>
      <el-button link type="primary" @click="$emit('cancel-reply')">取消</el-button>
    </div>

    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="4"
      :maxlength="300"
      show-word-limit
      :placeholder="replyingTo ? '补充你的回复内容…' : '写下你对这条路线的感受、提醒或补充建议…'"
      @update:model-value="$emit('update:modelValue', $event)"
    />

    <div class="composer-actions">
      <el-button v-if="replyingTo" round class="ghost-btn" @click="$emit('cancel-reply')">取消回复</el-button>
      <el-button type="primary" round class="primary-btn" :loading="loading" @click="$emit('submit')">{{ replyingTo ? '发表回复' : '发表评论' }}</el-button>
    </div>
  </div>

  <div v-else class="guest-shell">
    <p>登录后即可评论、回复并参与这条路线的讨论。</p>
    <el-button round type="primary" class="primary-btn" @click="$emit('login')">登录后评论</el-button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  replyingTo: {
    type: Object,
    default: null
  },
  loggedIn: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit', 'cancel-reply', 'login'])
</script>

<style scoped>
.composer-shell,
.guest-shell {
  padding: 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(188, 214, 255, 0.84);
  box-shadow: 0 14px 36px rgba(95, 138, 198, 0.08);
}

.reply-banner {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(237, 245, 255, 0.88);
  color: var(--brand-700);
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 14px;
}

.primary-btn {
  box-shadow: 0 12px 28px rgba(95, 158, 255, 0.24);
}

.ghost-btn {
  border-color: rgba(188, 214, 255, 0.84);
  background: rgba(255, 255, 255, 0.92);
  color: var(--text-strong);
}

.guest-shell {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.guest-shell p {
  margin: 0;
  color: var(--text-body);
  line-height: 1.7;
}

:deep(.el-textarea__inner) {
  border-radius: 18px;
  background: rgba(248, 252, 255, 0.96);
  box-shadow: 0 0 0 1px rgba(188, 214, 255, 0.84) inset;
}

@media (max-width: 700px) {
  .guest-shell,
  .reply-banner {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
