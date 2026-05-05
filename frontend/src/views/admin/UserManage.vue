<template>
  <div class="user-manage">
    <el-card shadow="never" class="manage-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">用户信息总览</div>
            <p class="card-subtitle">可按用户名搜索，并支持冻结或解封普通用户账号。</p>
          </div>
          <el-button :icon="RefreshRight" @click="fetchData">刷新</el-button>
        </div>
      </template>

      <div class="filter-container">
        <el-input
          v-model="searchQuery"
          placeholder="搜索用户名"
          class="search-input"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
      </div>

      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        border
        empty-text="暂无用户数据"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="180" />
        <el-table-column prop="nickname" label="昵称" min-width="160" />
        <el-table-column prop="createTime" label="注册时间" min-width="180" />
        <el-table-column label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'danger' : 'info'">
              {{ row.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '正常' : '已冻结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              type="warning"
              link
              :disabled="row.role === 1"
              @click="toggleStatus(row, 0)"
            >
              冻结账号
            </el-button>
            <el-button
              v-else
              type="success"
              link
              @click="toggleStatus(row, 1)"
            >
              解封账号
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '@/api/request'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchQuery = ref('')

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        username: searchQuery.value || undefined
      }
    })
    tableData.value = Array.isArray(res.records) ? res.records : []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (row, targetStatus) => {
  const actionName = targetStatus === 1 ? '解封' : '冻结'

  try {
    await ElMessageBox.confirm(
      `确定要${actionName}用户 “${row.username}” 吗？`,
      '账号状态变更',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      }
    )

    await request.patch(`/admin/users/${row.id}/status`, null, {
      params: { status: targetStatus }
    })
    ElMessage.success(`${actionName}成功`)
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error(error)
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.user-manage {
  width: 100%;
}

.manage-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.card-subtitle {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.filter-container {
  margin-bottom: 20px;
}

.search-input {
  width: min(320px, 100%);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
