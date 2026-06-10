<template>
  <div class="page-content">
    <PageHeader title="新建工作流" description="配置节点和连线，构建自动化流程">
      <template #actions>
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="content-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        label-position="right"
        class="wf-form"
        @submit.prevent
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="工作流名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="简要描述工作流的用途（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="工作流配置" prop="configText">
          <div class="json-editor-wrap">
            <div class="json-toolbar">
              <el-button size="small" @click="formatJson">
                <el-icon><Operation /></el-icon>
                <span>格式化</span>
              </el-button>
              <el-button size="small" @click="resetExample">
                <el-icon><RefreshLeft /></el-icon>
                <span>重置示例</span>
              </el-button>
            </div>
            <el-input
              v-model="form.configText"
              type="textarea"
              :rows="20"
              :autosize="{ minRows: 20, maxRows: 30 }"
              placeholder='{"nodes":[...], "edges":[...]}'
              class="json-textarea"
              @blur="validateJson"
            />
            <div v-if="jsonError" class="json-error">
              <el-icon><WarningFilled /></el-icon>
              {{ jsonError }}
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            创建
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Operation, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { createWorkflow } from '@/api/workflow'
import type { WorkflowCreateReq } from '@/types'

const router = useRouter()

// 预填示例：LLM 节点不需要单独配置 modelConfigId —— 会继承 Agent 绑定的模型
const EXAMPLE_JSON = JSON.stringify({
  nodes: [
    {
      nodeKey: 'classify', type: 'LLM', name: '意图识别',
      config: { prompt: '判断用户意图：{{start.userMessage}}。只输出 ORDER_QUERY、POLICY_QUERY 或 GENERAL。' },
      outputVariable: 'intent'
    },
    {
      nodeKey: 'cond', type: 'CONDITION', name: '是否订单',
      config: { expression: "{{classify.intent}} == 'ORDER_QUERY'" },
      outputVariable: 'isOrder'
    },
    {
      nodeKey: 'orderLLM', type: 'LLM', name: '订单回复',
      config: { prompt: '你是客服助手。用户问题：{{start.userMessage}}。意图：{{classify.intent}}。请直接回答。' },
      outputVariable: 'reply'
    },
    {
      nodeKey: 'generalLLM', type: 'LLM', name: '通用回复',
      config: { prompt: '你是客服助手。用户问题：{{start.userMessage}}。请直接回答。' },
      outputVariable: 'reply'
    },
  ],
  edges: [
    { sourceNodeKey: 'classify', targetNodeKey: 'cond' },
    { sourceNodeKey: 'cond', targetNodeKey: 'orderLLM', conditionExpr: 'true' },
    { sourceNodeKey: 'cond', targetNodeKey: 'generalLLM', conditionExpr: 'false' },
  ],
}, null, 2)

interface WfForm {
  name: string
  description: string
  configText: string
}

const form = ref<WfForm>({
  name: '',
  description: '',
  configText: EXAMPLE_JSON,
})

const formRef = ref<FormInstance>()
const submitting = ref(false)
const jsonError = ref('')

const rules: FormRules = {
  name: [
    { required: true, message: '请输入工作流名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度 1-100 个字符', trigger: 'blur' },
  ],
  configText: [
    { required: true, message: '请输入工作流 JSON 配置', trigger: 'blur' },
    { validator: (_rule, value, cb) => {
      if (!value) return cb(new Error('请输入工作流 JSON 配置'))
      const err = tryParseJson(value)
      if (err) return cb(new Error(err))
      cb()
    }, trigger: 'blur' },
  ],
}

function tryParseJson(text: string): string | null {
  try { JSON.parse(text); return null } catch (e) { return (e as Error).message }
}

function validateJson() {
  const err = tryParseJson(form.value.configText)
  jsonError.value = err || ''
}

function formatJson() {
  const parsed = JSON.parse(form.value.configText)
  form.value.configText = JSON.stringify(parsed, null, 2)
  jsonError.value = ''
  ElMessage.success('格式化完成')
}

function resetExample() {
  form.value.configText = EXAMPLE_JSON
  jsonError.value = ''
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const parsed = JSON.parse(form.value.configText)
    const payload: WorkflowCreateReq = {
      name: form.value.name,
      description: form.value.description || undefined,
      nodes: parsed.nodes ?? [],
      edges: parsed.edges ?? [],
    }
    await createWorkflow(payload)
    ElMessage.success('工作流创建成功')
    router.push('/workflows')
  } catch (e) {
    if (e instanceof SyntaxError) {
      ElMessage.error('JSON 格式错误')
    }
    // 其他错误由 request 拦截器处理
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wf-form {
  max-width: 800px;
}

.json-editor-wrap {
  width: 100%;
}
.json-toolbar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.json-textarea :deep(.el-textarea__inner) {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  tab-size: 2;
}
.json-error {
  margin-top: var(--space-2);
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--color-danger);
}
</style>
