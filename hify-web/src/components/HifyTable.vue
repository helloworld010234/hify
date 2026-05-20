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
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
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
const currentPage = ref(1)
const pageSize = ref(props.pageSize)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await props.api({
      page: currentPage.value,
      size: pageSize.value
    })
    tableData.value = res.data || []
    total.value = res.total || 0
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
    <div class="toolbar">
      <slot name="toolbar" />
    </div>
    <el-table :data="tableData" v-loading="loading" stripe style="width: 100%" row-key="id" @expand-change="(row: any, expandedRows: any[]) => $emit('expand-change', row, expandedRows)">
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
    <div class="pagination-wrapper">
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
.pagination-wrapper {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}
</style>
