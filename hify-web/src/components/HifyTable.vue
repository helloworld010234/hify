<script setup lang="ts">
import { ref, onMounted } from 'vue'

export interface TableColumn<T = any> {
  prop: string
  label: string
  width?: string | number
  minWidth?: string | number
  formatter?: (row: T, column: any, cellValue: any, index: number) => string
  slot?: string
}

export interface PageData<T = any> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

interface Props<T> {
  columns: TableColumn<T>[]
  api: (params: { page: number; size: number }) => Promise<PageData<T>>
  pageSize?: number
}

const props = withDefaults(defineProps<Props<any>>(), {
  pageSize: 10
})

const loading = ref(false)
const tableData = ref<any[]>([])
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = ref(props.pageSize)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await props.api({
      page: currentPage.value,
      size: pageSize.value
    })
    tableData.value = res.list || []
    total.value = res.total || 0
  } catch (err: any) {
    tableData.value = []
    total.value = 0
    errorMessage.value = err?.message || '列表加载失败'
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  currentPage.value = 1
  fetchData()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleToolbarRefresh = () => {
  refresh()
}

onMounted(() => {
  fetchData()
})

defineEmits<{
  'expand-change': [row: any, expandedRows: any[]]
}>()

defineExpose({
  refresh
})
</script>

<template>
  <div class="hify-table">
    <div class="toolbar" @keyup.enter="handleToolbarRefresh">
      <slot name="toolbar" :refresh="refresh" />
    </div>
    <el-table
      v-if="!errorMessage && (loading || tableData.length > 0)"
      :data="tableData"
      v-loading="loading"
      stripe
      style="width: 100%"
      row-key="id"
      @expand-change="(row: any, expandedRows: any[]) => $emit('expand-change', row, expandedRows)"
    >
      <el-table-column type="expand" v-if="$slots.expand">
        <template #default="scope">
          <slot name="expand" :row="scope.row" :$index="scope.$index" />
        </template>
      </el-table-column>
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :formatter="col.formatter"
      >
        <template #default="scope" v-if="col.slot">
          <slot :name="col.slot" :row="scope.row" :$index="scope.$index" :id="scope.row.id" />
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!loading && errorMessage" class="state-panel">
      <el-empty :description="errorMessage">
        <el-button type="primary" @click="fetchData">重试</el-button>
      </el-empty>
    </div>
    <div v-else-if="!loading && tableData.length === 0" class="state-panel">
      <el-empty description="暂无数据" />
    </div>

    <div v-if="total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hify-table {
  background: var(--color-bg-elevated);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-default);
  padding: var(--space-5);
}
.toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  gap: var(--space-3);
}
.state-panel {
  border: 1px dashed var(--color-border-default);
  border-radius: var(--radius-md);
  background: var(--color-bg-subtle);
}
.pagination-wrapper {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}
</style>
