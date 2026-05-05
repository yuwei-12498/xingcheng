<template>
  <div class="poi-manage">
    <el-card shadow="never" class="manage-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">POI 资源治理</div>
            <p class="card-subtitle">支持新增、编辑、删除与临时冻结。冻结后，该 POI 将不再参与行程推荐。</p>
          </div>
          <div class="header-actions">
            <el-button :icon="RefreshRight" @click="fetchData">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增 POI</el-button>
          </div>
        </div>
      </template>

      <div class="filter-container">
        <el-input
          v-model="searchQuery"
          placeholder="搜索 POI 名称"
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
        empty-text="暂无 POI 数据"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="district" label="行政区" width="120" />
        <el-table-column prop="priorityScore" label="权重" width="90" align="center" />
        <el-table-column label="营业时间" width="140" align="center">
          <template #default="{ row }">
            {{ formatBusinessHours(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.temporarilyClosed) === 1 ? 'danger' : 'success'">
              {{ Number(row.temporarilyClosed) === 1 ? '已冻结' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态备注" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.statusNote || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="特征" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="Number(row.indoor) === 1" size="small" class="tag-gap">室内</el-tag>
            <el-tag v-if="Number(row.nightAvailable) === 1" size="small" type="warning" class="tag-gap">夜游</el-tag>
            <el-tag v-if="Number(row.rainFriendly) === 1" size="small" type="success" class="tag-gap">雨天友好</el-tag>
            <span v-if="!hasFeatureTags(row)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button
              :type="Number(row.temporarilyClosed) === 1 ? 'success' : 'warning'"
              link
              @click="togglePoiStatus(row)"
            >
              {{ Number(row.temporarilyClosed) === 1 ? '解除冻结' : '临时冻结' }}
            </el-button>
            <el-button type="danger" link @click="deletePoi(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增 POI' : '编辑 POI'"
      width="820px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="poiForm"
        :rules="formRules"
        label-position="top"
        class="poi-form"
      >
        <div class="form-grid">
          <el-form-item label="名称" prop="name">
            <el-input v-model="poiForm.name" placeholder="请输入 POI 名称" />
          </el-form-item>

          <el-form-item label="分类" prop="category">
            <el-input v-model="poiForm.category" placeholder="例如：文化古迹、商业购物" />
          </el-form-item>

          <el-form-item label="行政区">
            <el-input v-model="poiForm.district" placeholder="例如：武侯区" />
          </el-form-item>

          <el-form-item label="建议停留时长（分钟）" prop="stayDuration">
            <el-input-number v-model="poiForm.stayDuration" :min="1" :step="10" class="full-width" />
          </el-form-item>

          <el-form-item label="营业开始时间">
            <el-time-picker
              v-model="poiForm.openTime"
              value-format="HH:mm:ss"
              format="HH:mm"
              placeholder="选择开始时间"
              clearable
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="营业结束时间">
            <el-time-picker
              v-model="poiForm.closeTime"
              value-format="HH:mm:ss"
              format="HH:mm"
              placeholder="选择结束时间"
              clearable
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="经度">
            <el-input-number
              v-model="poiForm.longitude"
              :precision="6"
              :step="0.000001"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="纬度">
            <el-input-number
              v-model="poiForm.latitude"
              :precision="6"
              :step="0.000001"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="人均消费（元）">
            <el-input-number
              v-model="poiForm.avgCost"
              :min="0"
              :precision="2"
              :step="10"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="系统权重" prop="priorityScore">
            <el-input-number
              v-model="poiForm.priorityScore"
              :min="0"
              :max="10"
              :precision="2"
              :step="0.5"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>

          <el-form-item label="步行强度">
            <el-select v-model="poiForm.walkingLevel" class="full-width">
              <el-option
                v-for="item in walkingLevelOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="闭馆日">
            <el-select
              v-model="poiForm.closedWeekdaysList"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              class="full-width"
              placeholder="可多选"
            >
              <el-option
                v-for="item in weekdayOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="临时冻结">
            <el-switch
              v-model="poiForm.temporarilyClosed"
              :active-value="1"
              :inactive-value="0"
              active-text="已冻结"
              inactive-text="正常"
            />
          </el-form-item>

          <el-form-item label="室内">
            <el-switch v-model="poiForm.indoor" :active-value="1" :inactive-value="0" />
          </el-form-item>

          <el-form-item label="夜游">
            <el-switch v-model="poiForm.nightAvailable" :active-value="1" :inactive-value="0" />
          </el-form-item>

          <el-form-item label="雨天友好">
            <el-switch v-model="poiForm.rainFriendly" :active-value="1" :inactive-value="0" />
          </el-form-item>

          <el-form-item label="地址" class="span-2">
            <el-input v-model="poiForm.address" placeholder="请输入详细地址" />
          </el-form-item>

          <el-form-item label="标签" class="span-2">
            <el-input v-model="poiForm.tags" placeholder="用逗号分隔，例如：文化,历史,打卡" />
          </el-form-item>

          <el-form-item label="适用人群" class="span-2">
            <el-input v-model="poiForm.suitableFor" placeholder="用逗号分隔，例如：亲子,情侣,独自" />
          </el-form-item>

          <el-form-item label="状态备注" class="span-2">
            <el-input
              v-model="poiForm.statusNote"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="例如：设备维护，暂停开放到 4 月底"
            />
          </el-form-item>

          <el-form-item label="简介" class="span-2">
            <el-input
              v-model="poiForm.description"
              type="textarea"
              :rows="4"
              placeholder="请输入 POI 简介"
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitPoi">
            {{ dialogMode === 'create' ? '创建 POI' : '保存修改' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import request from '@/api/request'
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const weekdayOptions = [
  { label: '周一', value: 'MONDAY' },
  { label: '周二', value: 'TUESDAY' },
  { label: '周三', value: 'WEDNESDAY' },
  { label: '周四', value: 'THURSDAY' },
  { label: '周五', value: 'FRIDAY' },
  { label: '周六', value: 'SATURDAY' },
  { label: '周日', value: 'SUNDAY' }
]

const walkingLevelOptions = ['低', '中', '高']

const createEmptyPoiForm = () => ({
  id: null,
  name: '',
  category: '',
  district: '',
  address: '',
  latitude: null,
  longitude: null,
  openTime: '',
  closeTime: '',
  closedWeekdaysList: [],
  temporarilyClosed: 0,
  statusNote: '',
  avgCost: 0,
  stayDuration: 90,
  indoor: 0,
  nightAvailable: 0,
  rainFriendly: 0,
  walkingLevel: '中',
  tags: '',
  suitableFor: '',
  description: '',
  priorityScore: 3
})

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchQuery = ref('')
const tableData = ref([])
const formRef = ref(null)
const poiForm = reactive(createEmptyPoiForm())
const initialTemporarilyClosed = ref(0)

const formRules = {
  name: [{ required: true, message: '请输入 POI 名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入 POI 分类', trigger: 'blur' }],
  stayDuration: [{ required: true, message: '请输入建议停留时长', trigger: 'change' }],
  priorityScore: [{ required: true, message: '请输入系统权重', trigger: 'change' }]
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/pois', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        name: searchQuery.value || undefined
      }
    })
    tableData.value = Array.isArray(res.records) ? res.records : []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

const openCreateDialog = async () => {
  dialogMode.value = 'create'
  Object.assign(poiForm, createEmptyPoiForm())
  initialTemporarilyClosed.value = Number(poiForm.temporarilyClosed || 0)
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

const openEditDialog = async (row) => {
  dialogMode.value = 'edit'
  Object.assign(poiForm, createEmptyPoiForm(), mapRowToForm(row))
  initialTemporarilyClosed.value = Number(row.temporarilyClosed ?? 0)
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

const confirmStatusChangeIfNeeded = async () => {
  const nextFrozen = Number(poiForm.temporarilyClosed || 0)
  if (nextFrozen === initialTemporarilyClosed.value) {
    return true
  }

  if (nextFrozen === 1) {
    const { value } = await ElMessageBox.prompt(
      `请输入冻结说明。冻结后，POI “${poiForm.name}” 将不再参与行程推荐。`,
      '临时冻结 POI',
      {
        confirmButtonText: '确认冻结',
        cancelButtonText: '取消',
        inputValue: poiForm.statusNote || '管理员已暂时冻结该 POI。',
        inputPlaceholder: '例如：场馆维护，暂停开放到 4 月底',
        inputValidator: (input) => {
          if (!input || !input.trim()) {
            return '请输入冻结说明'
          }
          return true
        }
      }
    )
    poiForm.statusNote = value.trim()
    return true
  }

  await ElMessageBox.confirm(
    `确定解除冻结 POI “${poiForm.name}” 吗？恢复后它会重新参与行程推荐。`,
    '解除冻结',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  )
  poiForm.statusNote = ''
  return true
}

const submitPoi = async () => {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    await confirmStatusChangeIfNeeded()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = buildPoiPayload()
    if (dialogMode.value === 'create') {
      await request.post('/admin/pois', payload)
      ElMessage.success('POI 创建成功')
      currentPage.value = 1
    } else {
      await request.put('/admin/pois', payload)
      ElMessage.success('POI 更新成功')
    }
    initialTemporarilyClosed.value = Number(poiForm.temporarilyClosed || 0)
    dialogVisible.value = false
    await fetchData()
  } finally {
    submitting.value = false
  }
}

const togglePoiStatus = async (row) => {
  const isFrozen = Number(row.temporarilyClosed) === 1

  if (isFrozen) {
    try {
      await ElMessageBox.confirm(
        `确定解除冻结 POI “${row.name}” 吗？恢复后它会重新参与行程推荐。`,
        '解除冻结',
        {
          type: 'warning',
          confirmButtonText: '确定',
          cancelButtonText: '取消'
        }
      )
      await request.patch(`/admin/pois/${row.id}/status`, null, {
        params: {
          temporarilyClosed: 0,
          statusNote: ''
        }
      })
      ElMessage.success('POI 已解除冻结')
      await fetchData()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') {
        console.error(error)
      }
    }
    return
  }

  try {
    const { value } = await ElMessageBox.prompt(
      `请输入冻结说明。冻结后，POI “${row.name}” 将不再参与行程推荐。`,
      '临时冻结 POI',
      {
        confirmButtonText: '确认冻结',
        cancelButtonText: '取消',
        inputValue: row.statusNote || '管理员已暂时冻结该 POI。',
        inputPlaceholder: '例如：场馆维护，暂停开放到 4 月底'
      }
    )

    await request.patch(`/admin/pois/${row.id}/status`, null, {
      params: {
        temporarilyClosed: 1,
        statusNote: value || undefined
      }
    })
    ElMessage.success('POI 已临时冻结')
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error(error)
    }
  }
}

const deletePoi = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除后，POI “${row.name}” 将从系统中移除，确定继续吗？`,
      '删除 POI',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消'
      }
    )

    await request.delete(`/admin/pois/${row.id}`)
    ElMessage.success('POI 已删除')

    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }

    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error(error)
    }
  }
}

const mapRowToForm = (row) => ({
  id: row.id,
  name: row.name || '',
  category: row.category || '',
  district: row.district || '',
  address: row.address || '',
  latitude: row.latitude == null ? null : Number(row.latitude),
  longitude: row.longitude == null ? null : Number(row.longitude),
  openTime: normalizeTimeValue(row.openTime),
  closeTime: normalizeTimeValue(row.closeTime),
  closedWeekdaysList: splitClosedWeekdays(row.closedWeekdays),
  temporarilyClosed: Number(row.temporarilyClosed ?? 0),
  statusNote: row.statusNote || '',
  avgCost: row.avgCost == null ? 0 : Number(row.avgCost),
  stayDuration: row.stayDuration == null ? 90 : Number(row.stayDuration),
  indoor: Number(row.indoor ?? 0),
  nightAvailable: Number(row.nightAvailable ?? 0),
  rainFriendly: Number(row.rainFriendly ?? 0),
  walkingLevel: row.walkingLevel || '中',
  tags: row.tags || '',
  suitableFor: row.suitableFor || '',
  description: row.description || '',
  priorityScore: row.priorityScore == null ? 3 : Number(row.priorityScore)
})

const buildPoiPayload = () => ({
  id: poiForm.id || undefined,
  name: trimText(poiForm.name),
  category: trimText(poiForm.category),
  district: trimText(poiForm.district),
  address: trimText(poiForm.address),
  latitude: poiForm.latitude,
  longitude: poiForm.longitude,
  openTime: poiForm.openTime || null,
  closeTime: poiForm.closeTime || null,
  closedWeekdays: poiForm.closedWeekdaysList.length ? poiForm.closedWeekdaysList.join(',') : null,
  temporarilyClosed: Number(poiForm.temporarilyClosed || 0),
  statusNote: trimText(poiForm.statusNote),
  avgCost: poiForm.avgCost == null ? 0 : poiForm.avgCost,
  stayDuration: poiForm.stayDuration,
  indoor: Number(poiForm.indoor || 0),
  nightAvailable: Number(poiForm.nightAvailable || 0),
  rainFriendly: Number(poiForm.rainFriendly || 0),
  walkingLevel: trimText(poiForm.walkingLevel) || '中',
  tags: trimText(poiForm.tags),
  suitableFor: trimText(poiForm.suitableFor),
  description: trimText(poiForm.description),
  priorityScore: poiForm.priorityScore
})

const splitClosedWeekdays = (raw) => {
  if (!raw) {
    return []
  }
  return String(raw)
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

const normalizeTimeValue = (value) => {
  if (!value) {
    return ''
  }
  const text = String(value)
  if (/^\d{2}:\d{2}$/.test(text)) {
    return `${text}:00`
  }
  return text
}

const trimText = (value) => {
  if (value == null) {
    return null
  }
  const trimmed = String(value).trim()
  return trimmed ? trimmed : null
}

const formatBusinessHours = (row) => {
  if (!row.openTime || !row.closeTime) {
    return '未设置'
  }
  return `${String(row.openTime).slice(0, 5)} - ${String(row.closeTime).slice(0, 5)}`
}

const hasFeatureTags = (row) =>
  Number(row.indoor) === 1 || Number(row.nightAvailable) === 1 || Number(row.rainFriendly) === 1

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.poi-manage {
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

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-container {
  margin-bottom: 20px;
}

.search-input {
  width: min(360px, 100%);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.tag-gap {
  margin-right: 4px;
}

.poi-form {
  padding-top: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.span-2 {
  grid-column: 1 / -1;
}

.full-width {
  width: 100%;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }
}
</style>
