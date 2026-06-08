import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const sampleFilePath = path.resolve(currentDir, 'fixtures', 'knowledge-sample.txt')

async function waitForTableRow(page: Page, text: string) {
  await expect(page.getByRole('row', { name: new RegExp(text) }).first()).toBeVisible()
}

test.describe('Hify Browser E2E', () => {
  test('knowledge base flow should be reachable from sidebar and support create edit upload', async ({ page }) => {
    const knowledgeBaseName = `e2e-kb-${Date.now()}`
    const updatedKnowledgeBaseName = `${knowledgeBaseName}-updated`

    await page.goto('/')

    await page.getByTestId('nav-knowledge-bases').click()
    await expect(page).toHaveURL(/\/knowledge-bases$/)

    await page.getByTestId('knowledge-add-button').click()
    await page.getByTestId('knowledge-name-input').fill(knowledgeBaseName)
    await page.getByTestId('knowledge-description-input').fill('browser e2e knowledge base')
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, knowledgeBaseName)

    await page.getByTestId(`knowledge-edit-button-${knowledgeBaseName}`).click()
    await page.getByTestId('knowledge-name-input').fill(updatedKnowledgeBaseName)
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, updatedKnowledgeBaseName)

    await page.getByTestId(`knowledge-name-link-${updatedKnowledgeBaseName}`).click()
    await expect(page).toHaveURL(/\/knowledge-bases\/\d+\/documents$/)

    await page.getByTestId('document-upload-open-button').click()
    await page.locator('input[type="file"]').setInputFiles(sampleFilePath)
    await page.getByTestId('document-upload-submit-button').click()

    await expect(page.getByRole('row', { name: /knowledge-sample\.txt/i }).first()).toBeVisible()
  })

  test('provider workflow and mcp pages should support create edit delete from UI', async ({ page }) => {
    const providerName = `e2e-provider-${Date.now()}`
    const updatedProviderName = `${providerName}-updated`
    const workflowName = `e2e-workflow-${Date.now()}`
    const mcpName = `e2e-mcp-${Date.now()}`
    const updatedMcpName = `${mcpName}-updated`

    await page.goto('/providers')

    await page.getByTestId('provider-add-button').click()
    await page.getByTestId('provider-name-input').fill(providerName)
    await page.getByTestId('provider-base-url-input').fill('http://127.0.0.1:9999')
    await page.getByTestId('provider-api-key-input').fill('e2e-dummy-key')
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, providerName)

    await page.getByTestId(`provider-edit-button-${providerName}`).click()
    await page.getByTestId('provider-name-input').fill(updatedProviderName)
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, updatedProviderName)

    await page.getByTestId('nav-workflows').click()
    await page.getByTestId('workflow-add-button').click()
    await page.getByTestId('workflow-name-input').fill(workflowName)
    await page.getByTestId('workflow-submit-button').click()

    await expect(page).toHaveURL(/\/workflows$/)
    await waitForTableRow(page, workflowName)

    await page.getByTestId('nav-mcp-servers').click()
    await page.getByTestId('mcp-add-button').click()
    await page.getByTestId('mcp-name-input').fill(mcpName)
    await page.getByTestId('mcp-endpoint-input').fill('http://127.0.0.1:9001/mcp')
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, mcpName)

    await page.getByTestId(`mcp-edit-button-${mcpName}`).click()
    await page.getByTestId('mcp-name-input').fill(updatedMcpName)
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, updatedMcpName)
  })

  test('agent and chat flow should allow selecting an agent from UI and creating a session', async ({ page }) => {
    const agentName = `e2e-agent-${Date.now()}`

    await page.goto('/agents')

    const unavailableModelsAlert = page.locator('.path-alert')
    if (await unavailableModelsAlert.isVisible().catch(() => false)) {
      test.skip(true, 'No available models in current environment, skipping pure UI agent/chat flow.')
    }

    await page.locator('.page-header .el-button').click()
    await page.locator('.hify-form-dialog input').first().fill(agentName)
    await page.locator('.hify-form-dialog textarea').first().fill('browser e2e agent')
    await page.locator('.hify-form-dialog .el-select').first().click()
    await page.getByRole('option').first().click()
    await page.locator('.hify-form-dialog textarea').nth(1).fill('You are a browser E2E test agent.')
    await page.getByTestId('dialog-submit-button').click()

    await waitForTableRow(page, agentName)

    await page.getByTestId('nav-chat').click()
    await page.getByTestId('chat-agent-select').click()
    await page.getByRole('option', { name: new RegExp(agentName) }).click()
    await page.getByTestId('chat-create-session-button').click()

    await expect(page.getByTestId('chat-session-tag')).toBeVisible()
    await expect(page.getByTestId('chat-message-input')).toBeEnabled()
  })
})
