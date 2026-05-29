<template>
  <div class="page shell">
    <header class="bar glass">
      <div class="bar-left">
        <el-button round @click="$router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
      </div>
      <div class="bar-center">
        <h2>{{ kb?.name || '知识库' }}</h2>
        <p v-if="kb?.description" class="kb-sub">{{ kb.description }}</p>
      </div>
      <div class="bar-right">
        <el-button size="small" round @click="$router.push(`/kb/${$route.params.id}/insights`)">数据洞察</el-button>
        <el-button size="small" round @click="$router.push(`/kb/${$route.params.id}/graph`)">知识图谱</el-button>
        <template v-if="isOwner">
          <el-button size="small" round @click="openKbEditDialog">编辑信息</el-button>
          <el-button size="small" round type="primary" @click="openMemberDialog">成员管理</el-button>
        </template>
      </div>
    </header>

    <div class="kb-layout">
      <el-card class="doc-card">
        <template #header>
          <div class="doc-card-head">
            <div class="doc-card-title">
              <el-icon class="doc-head-ico"><FolderOpened /></el-icon>
              <span>文档中心</span>
            </div>
            <el-tag v-if="canWrite" size="small" round effect="plain" type="info">可上传</el-tag>
            <el-tag v-else size="small" round effect="plain">只读</el-tag>
          </div>
        </template>
        <div class="doc-card-body">
          <div class="doc-viz-row">
            <div ref="docDonutRef" class="doc-donut" />
            <div class="doc-metrics">
              <div class="metric" v-for="m in docMetricItems" :key="m.key">
                <span class="metric-val">{{ m.value }}</span>
                <span class="metric-label">{{ m.label }}</span>
              </div>
            </div>
          </div>
          <div class="doc-progress-wrap">
            <el-progress
              :percentage="docReadyPercent"
              :stroke-width="8"
              :show-text="false"
              :status="hasRunningDocs ? '' : docReadyPercent === 100 ? 'success' : ''"
            />
            <span class="doc-progress-text">整体处理进度：{{ docReadyPercent }}%</span>
          </div>
          <div class="upload-hero" v-if="canWrite">
            <el-upload
              drag
              multiple
              class="upload-drag"
              :show-file-list="true"
              :http-request="handleCustomUpload"
              accept=".pdf,.docx,.md,.txt,.markdown,.png,.jpg,.jpeg,.webp,.gif,.mp4,.mov,.m4v,.webm,.avi"
              :before-upload="beforeUpload"
            >
              <el-icon class="upload-ico"><UploadFilled /></el-icon>
              <div class="upload-copy">
                <p class="upload-title">拖拽多个文件到此处，或点击批量上传</p>
                <p class="upload-sub">
                  支持 pdf · docx · md · txt · markdown · png / jpg / webp / gif · mp4 / mov / m4v / webm / avi
                  （图片将生成视觉描述参与检索，视频将自动抽帧并生成多模态摘要）
                </p>
              </div>
            </el-upload>
            <el-checkbox v-model="uploadOverwrite" class="upload-overwrite">覆盖重复文档（同内容哈希）</el-checkbox>
            <div class="url-import glass">
              <div class="url-import-row">
                <span class="url-import-label">网页抓取</span>
                <el-input
                  v-model="importUrl"
                  clearable
                  placeholder="https:// 公开可访问页面"
                  class="url-import-input"
                />
                <el-button type="primary" :loading="importing" @click="submitUrlImport">抓取入库</el-button>
              </div>
              <div class="url-import-options">
                <span class="url-import-opt-label">抓取内容（多选）</span>
                <el-checkbox-group v-model="urlImportModes">
                  <el-checkbox value="text">正文文本</el-checkbox>
                  <el-checkbox value="images">页面图片</el-checkbox>
                  <el-checkbox value="videos">视频 / 嵌入链接</el-checkbox>
                </el-checkbox-group>
                <p class="url-import-hint">
                  仅抓图或仅抓视频链接时，请取消勾选「正文文本」，否则会额外生成一个 .txt。
                </p>
                <div v-if="urlImportModes.includes('images')" class="url-import-maximg">
                  <span>图片上限</span>
                  <el-input-number v-model="urlImportMaxImages" :min="1" :max="50" size="small" />
                </div>
              </div>
            </div>
          </div>
          <el-alert v-else type="info" :closable="false" title="当前为只读成员，无法上传" />
          <el-alert
            v-if="hasRunningDocs"
            type="success"
            :closable="false"
            title="检测到文档正在解析，列表将自动刷新"
          />
          <div class="doc-tools">
            <el-input
              v-model="docKeyword"
              clearable
              size="small"
              placeholder="按文件名搜索"
              class="doc-tool-search"
            />
            <el-select v-model="docStatusFilter" size="small" class="doc-tool-select" @change="loadDocs">
              <el-option label="全部状态" value="ALL" />
              <el-option label="待处理" value="PENDING" />
              <el-option label="解析中" value="PROCESSING" />
              <el-option label="可用" value="READY" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-select v-model="docExtFilter" clearable size="small" class="doc-tool-select" placeholder="类型" @change="loadDocs">
              <el-option label="全部类型" value="" />
              <el-option label="PDF" value=".pdf" />
              <el-option label="Word" value=".docx" />
              <el-option label="Markdown" value=".md" />
              <el-option label="文本" value=".txt" />
              <el-option label="图片" value=".png" />
              <el-option label="JPG" value=".jpg" />
              <el-option label="WebP" value=".webp" />
              <el-option label="GIF" value=".gif" />
              <el-option label="MP4" value=".mp4" />
              <el-option label="MOV" value=".mov" />
              <el-option label="WebM" value=".webm" />
              <el-option label="AVI" value=".avi" />
            </el-select>
            <el-input
              v-model="docTagFilter"
              clearable
              size="small"
              class="doc-tool-tag"
              placeholder="标签关键字"
              @clear="loadDocs"
              @keyup.enter="loadDocs"
            />
            <el-button size="small" @click="loadDocs">筛选</el-button>
            <el-select v-model="docSortOrder" size="small" class="doc-tool-select" @change="loadDocs">
              <el-option label="最新优先" value="DESC" />
              <el-option label="最早优先" value="ASC" />
            </el-select>
          </div>
          <div v-if="canWrite && selectedDocIds.length" class="batch-bar">
            <el-button type="danger" size="small" round @click="batchDeleteDocs">
              批量删除（{{ selectedDocIds.length }}）
            </el-button>
            <el-button size="small" round @click="clearDocSelection">取消选择</el-button>
          </div>
          <div class="doc-table-wrap">
            <el-table
              ref="docTableRef"
              :data="filteredDocs"
              :row-key="docRowKey"
              v-loading="docLoading"
              class="doc-table"
              :height="docTableHeight"
              stripe
              @selection-change="onDocSelection"
            >
            <el-table-column v-if="canWrite" type="selection" width="44" />
            <el-table-column prop="filename" label="文件名" min-width="160" />
            <el-table-column label="标签" min-width="120">
              <template #default="{ row }">
                <div class="tag-cell">
                  <el-tag
                    v-for="(t, ti) in (row.tags || []).slice(0, 3)"
                    :key="ti"
                    size="small"
                    effect="plain"
                    class="mini-tag"
                  >
                    {{ t }}
                  </el-tag>
                  <el-button v-if="canWrite" link type="primary" size="small" @click.stop="openTagDialog(row)">
                    编辑
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="170">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
                <el-tooltip
                  v-if="row.status === 'FAILED' && row.errorMessage"
                  effect="dark"
                  placement="top"
                  :content="row.errorMessage"
                >
                  <el-icon class="err-icon"><Warning /></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="处理阶段" width="160">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ stageLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="canWrite" label="操作" width="330">
              <template #default="{ row }">
                <el-button
                  v-if="canPreview(row)"
                  link
                  size="small"
                  type="primary"
                  @click.stop="openPreview(row)"
                >
                  预览
                </el-button>
                <el-button
                  v-if="row.status === 'FAILED' && row.errorMessage"
                  link
                  size="small"
                  @click="openDocError(row)"
                >
                  查看失败
                </el-button>
                <el-button
                  v-if="row.status === 'FAILED'"
                  link
                  type="warning"
                  @click="reindexDoc(row)"
                >
                  重试解析
                </el-button>
                <el-button
                  v-else
                  link
                  type="primary"
                  @click="reindexDoc(row)"
                >
                  重建索引
                </el-button>
                <el-button link type="danger" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="doc-empty-hint">
                <el-icon><Document /></el-icon>
                <span>暂无文档，上传后在此查看解析状态与索引情况</span>
              </div>
            </template>
          </el-table>
          </div>
        </div>
      </el-card>

      <el-card class="rag-card">
        <template #header>
          <div class="rag-card-title">
            <div class="rag-title-left">
              <span class="rag-h">RAG 智能问答</span>
              <el-tag size="small" effect="dark" round type="primary">检索增强</el-tag>
            </div>
            <span class="rag-title-hint">仅依据知识库片段作答 · 可展开引用核验</span>
          </div>
        </template>
        <el-tabs v-model="ragTab" class="rag-tabs" stretch>
          <el-tab-pane label="智能问答" name="ask">
            <div class="rag-tab-scroll">
          <el-alert
            v-if="!canAsk"
            type="warning"
            :closable="false"
            :title="askDisabledHint"
            class="rag-alert"
          />
          <div class="quick-prompts">
            <span class="quick-label">快捷意图</span>
            <div class="quick-chips">
              <el-button
                v-for="qp in quickPrompts"
                :key="qp.text"
                size="small"
                round
                :disabled="!canAsk"
                @click="applyQuickPrompt(qp)"
              >
                {{ qp.label }}
              </el-button>
            </div>
          </div>
          <el-input
            v-model="question"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 14 }"
            class="q-area"
            placeholder="描述你的问题。支持「总结 / 对比 / 步骤 / 表格」等表述，也可直接点上方快捷意图。"
            maxlength="1000"
            show-word-limit
          />
          <div class="q-toolbar">
            <span class="question-meta">{{ question.length }} 字</span>
            <el-tag
              size="small"
              round
              effect="plain"
              :type="nextChatSessionMode.type"
              class="session-mode-tag"
            >
              {{ nextChatSessionMode.label }}
            </el-tag>
            <el-button link type="primary" size="small" :disabled="!question.trim()" @click="clearQuestion">
              清空输入
            </el-button>
            <el-button link type="primary" size="small" @click="startNewChat">
              {{ nextChatSessionMode.isContinuation ? '切换为新会话' : '新对话' }}
            </el-button>
          </div>
          <el-collapse v-model="advOpen" class="adv-collapse">
            <el-collapse-item title="高级参数" name="adv">
              <div class="adv-grid">
                <div class="adv-item">
                  <div class="adv-label">
                    上下文片段数
                    <el-tooltip content="送入模型的知识库片段数量，越多越全面，但噪声与耗时可能上升。" placement="top">
                      <el-icon class="adv-tip"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <el-slider v-model="chatSettings.contextChunks" :min="1" :max="10" :step="1" show-stops />
                </div>
                <div class="adv-item">
                  <div class="adv-label">
                    温度
                    <el-tooltip content="越高越有发散性，越低越稳定。一般知识问答建议 0.4–0.9。" placement="top">
                      <el-icon class="adv-tip"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <el-slider v-model="chatSettings.temperature" :min="0" :max="1.5" :step="0.05" show-input />
                </div>
                <div class="adv-item">
                  <div class="adv-label">top_p</div>
                  <el-slider v-model="chatSettings.topP" :min="0.1" :max="1" :step="0.05" show-input />
                </div>
                <div class="adv-item">
                  <div class="adv-label">top_k</div>
                  <el-slider v-model="chatSettings.topK" :min="1" :max="100" :step="1" show-input />
                </div>
                <div class="adv-item full">
                  <div class="adv-label">回答风格</div>
                  <el-radio-group v-model="chatSettings.answerStyle" size="small">
                    <el-radio-button value="BRIEF">精炼</el-radio-button>
                    <el-radio-button value="NORMAL">平衡</el-radio-button>
                    <el-radio-button value="DETAILED">详尽</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <div class="chat-actions chat-actions-main">
            <div class="stream-toggle">
              <span class="stream-label">流式输出</span>
              <el-tooltip content="开启后像 ChatGPT 一样逐字出现（需 Ollama 支持流式）" placement="top">
                <el-switch v-model="useStream" size="small" />
              </el-tooltip>
            </div>
            <el-button
              type="primary"
              :loading="chatLoading"
              :disabled="!canAsk"
              round
              class="btn-gen"
              @click="ask"
            >
              <el-icon><Promotion /></el-icon>
              生成回答
            </el-button>
            <el-button round :disabled="!canAsk || !question.trim() || chatLoading" @click="ask">
              <el-icon><RefreshRight /></el-icon>
              重新生成
            </el-button>
            <el-button round :disabled="!answer" @click="exportCurrentQa">
              <el-icon><Download /></el-icon>
              导出 Markdown
            </el-button>
          </div>
          <div v-if="lastChatSessionMode" class="meta-row meta-row-session">
            <el-tag round effect="plain" :type="lastChatSessionMode.type">{{ lastChatSessionMode.label }}</el-tag>
          </div>
          <el-alert
            v-if="nextChatSessionMode.isContinuation"
            class="session-mode-alert"
            type="warning"
            :closable="false"
            title="下一次生成将延续上一轮会话上下文"
            description="如果你想完全按当前问题重新分析，请先点“切换为新会话”，再生成回答。"
          />
          <div v-if="suggestedQuestions.length" class="suggestions">
            <span class="suggestions-title">相关问题推荐</span>
            <div class="suggestion-list">
              <el-button
                v-for="(s, i) in suggestedQuestions"
                :key="i"
                link
                type="primary"
                size="small"
                @click="useSuggestion(s)"
              >
                {{ s }}
              </el-button>
            </div>
          </div>
          <div v-if="lastMeta" class="meta-row">
            <el-tag round effect="plain" type="info">检索命中 {{ lastMeta.retrievedCandidates }} 条</el-tag>
            <el-tag round effect="plain" type="success">使用片段 {{ lastMeta.contextChunksUsed }} 条</el-tag>
            <el-tag round effect="plain">耗时 {{ lastMeta.latencyMs }} ms</el-tag>
          </div>
          <div v-if="chatLoading" class="answer-loading gen-loading">
            <div class="gen-orbit" aria-hidden="true">
              <div class="gen-core" />
            </div>
            <p class="loading-hint">正在检索向量并调用大模型…</p>
            <p class="loading-sub">{{ useStream ? '流式输出已开启，回答将逐段显示' : '一次性生成模式' }}</p>
          </div>
          <div v-else-if="answer" class="answer answer-panel rag-split">
            <div class="rag-split-main">
              <div class="answer-head">
                <div class="answer-head-left">
                  <span class="answer-badge">回答</span>
                  <span class="answer-hint">Markdown · 代码高亮 · KaTeX 公式 · Mermaid 图 · 点击 [n] 定位引用</span>
                </div>
                <div class="answer-actions">
                  <el-button size="small" round @click="copyText(question, '问题')">
                    <el-icon><DocumentCopy /></el-icon>
                    复制问题
                  </el-button>
                  <el-button size="small" round @click="copyText(answer, '回答')">
                    <el-icon><DocumentCopy /></el-icon>
                    复制回答
                  </el-button>
                  <el-button size="small" round type="primary" plain @click="copyRenderedMarkdown">
                    <el-icon><Tickets /></el-icon>
                    复制 MD 源码
                  </el-button>
                </div>
              </div>
              <div class="answer-scroll">
                <div ref="mdAnswerRef" class="md-body" v-html="renderedAnswerHtml" @click="onAnswerClick" />
              </div>
              <div v-if="citations.length" class="source-tags">
                <span class="source-tags-label">来源</span>
                <el-tag
                  v-for="(c, i) in citations"
                  :key="i"
                  size="small"
                  round
                  effect="plain"
                  class="source-tag"
                  @click="openCitationPreview(c, i)"
                >
                  {{ citationTagLabel(c, i) }}
                </el-tag>
              </div>
              <div v-if="currentHistoryId" class="answer-feedback">
                <span class="fb-label">本次回答反馈</span>
                <el-button size="small" round @click="feedbackCurrent(true)">👍 有用</el-button>
                <el-button size="small" round @click="feedbackCurrent(false)">👎 无用</el-button>
              </div>
            </div>
            <aside class="ctx-aside" :class="{ collapsed: ctxCollapsed }">
              <div class="ctx-aside-head">
                <span class="ctx-title">
                  <el-icon><Reading /></el-icon>
                  检索上下文（{{ citations.length }}）
                </span>
                <el-button text size="small" type="primary" @click="ctxCollapsed = !ctxCollapsed">
                  {{ ctxCollapsed ? '展开' : '折叠' }}
                </el-button>
              </div>
              <el-scrollbar v-show="!ctxCollapsed" class="ctx-scroll" height="320">
                <div v-if="citations.length" class="citation-list ctx-citations">
                  <div v-for="(c, i) in citations" :id="`cite-${i + 1}`" :key="i" class="cite-card">
                    <div class="cite-top">
                      <span class="cite-idx">{{ i + 1 }}</span>
                      <span class="cite-name">{{ c.documentName || '文档' }}</span>
                      <el-tag v-if="c.modality === 'image'" size="small" round type="success" effect="plain"
                        >图片</el-tag
                      >
                      <el-tag v-if="c.pageNo != null" size="small" round effect="plain">第 {{ c.pageNo }} 页</el-tag>
                      <el-tag
                        v-if="c.lineStart != null"
                        size="small"
                        round
                        effect="plain"
                        type="info"
                      >
                        行 {{ c.lineStart }}{{ c.lineEnd != null && c.lineEnd !== c.lineStart ? '–' + c.lineEnd : '' }}
                      </el-tag>
                      <el-button
                        v-if="c.sourceUrl"
                        size="small"
                        link
                        type="primary"
                        tag="a"
                        :href="c.sourceUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        @click.stop
                      >
                        原文链接
                      </el-button>
                      <el-button size="small" link type="primary" @click="copyText(c.excerpt, '引用片段')">
                        复制
                      </el-button>
                    </div>
                    <div v-if="c.modality === 'image' && citationThumbUrls[i]" class="cite-thumb-wrap">
                      <img :src="citationThumbUrls[i]" alt="引用图片" class="cite-thumb" />
                    </div>
                    <pre class="cite-body">{{ c.excerpt }}</pre>
                  </div>
                </div>
                <el-empty v-else description="暂无引用" />
              </el-scrollbar>
            </aside>
          </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="问答历史" name="history">
            <div class="history-tab">
              <div class="history-head">
                <h4 class="history-title">最近问答</h4>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :disabled="chatHistory.length === 0"
                  @click="clearHistory"
                >
                  清空
                </el-button>
              </div>
              <el-input
                v-model="historyKeyword"
                size="small"
                clearable
                placeholder="搜索历史（问题/回答）"
                class="history-search"
              />
              <el-scrollbar :height="historyScrollHeight" class="history-scroll">
                <div v-if="filteredHistory.length === 0" class="history-empty">暂无记录</div>
                <div
                  v-for="item in filteredHistory"
                  :key="item.id"
                  class="history-item"
                  @click="applyHistorySwitchTab(item)"
                >
                  <div class="history-rate" @click.stop>
                    <el-button
                      link
                      size="small"
                      :type="item.helpful === true ? 'success' : 'info'"
                      @click="feedbackHistory(item, true)"
                    >👍 有用</el-button>
                    <el-button
                      link
                      size="small"
                      :type="item.helpful === false ? 'danger' : 'info'"
                      @click="feedbackHistory(item, false)"
                    >👎 无用</el-button>
                  </div>
                  <p class="history-q">
                    <strong>问：</strong>{{ historyHeading(item) }}
                  </p>
                  <p v-if="item.sessionTitle && item.question" class="history-q-sub">{{ item.question }}</p>
                  <p class="history-a"><strong>答：</strong>{{ item.answer }}</p>
                  <div class="history-footer">
                    <p class="history-time">{{ formatTime(item.createdAt) }}</p>
                    <div class="history-actions">
                      <el-button link size="small" @click.stop="copyText(item.question, '问题')">复制问</el-button>
                      <el-button link size="small" @click.stop="copyText(item.answer, '回答')">复制答</el-button>
                      <el-button
                        link
                        type="danger"
                        size="small"
                        @click.stop="deleteHistory(item)"
                      >
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>
              </el-scrollbar>
              <div class="history-pager">
                <el-pagination
                  v-model:current-page="historyPageUi"
                  v-model:page-size="historySize"
                  :total="historyTotal"
                  :page-sizes="[5, 10, 20]"
                  layout="total, sizes, prev, pager, next"
                  small
                  @current-change="loadChatHistory"
                  @size-change="onHistorySizeChange"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <el-dialog v-model="memberVisible" title="成员管理" width="520px" @open="loadMembers">
      <el-table :data="members" v-loading="memberLoading" size="small">
        <el-table-column prop="username" label="用户" />
        <el-table-column prop="permission" label="权限" width="100" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <el-form :inline="true" @submit.prevent="addMember">
        <el-form-item label="用户名">
          <el-input v-model="newMemberUsername" placeholder="已注册用户名" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="newMemberPerm" style="width: 100px">
            <el-option label="只读" value="READ" />
            <el-option label="读写" value="WRITE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="addMember">添加 / 更新</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">同一用户再次添加将更新其权限。</p>
    </el-dialog>

    <el-dialog v-model="kbEditVisible" title="编辑知识库" width="480px">
      <el-form>
        <el-form-item label="名称">
          <el-input v-model="kbEditForm.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kbEditForm.description" type="textarea" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbEditSaving" @click="saveKbEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="citationPreviewVisible" title="引用片段" width="560px" destroy-on-close>
      <template v-if="citationPreview">
        <div v-if="citationPreviewImageUrl" class="cite-preview-img-wrap">
          <img :src="citationPreviewImageUrl" alt="原始图片" class="cite-preview-img" />
        </div>
        <p class="cite-preview-meta">
          <strong>文档：</strong>{{ citationPreview.documentName || '—' }}
        </p>
        <p v-if="citationPreview.pageNo != null" class="cite-preview-meta">
          <strong>页码：</strong>第 {{ citationPreview.pageNo }} 页
        </p>
        <p v-if="citationPreview.lineStart != null" class="cite-preview-meta">
          <strong>行号：</strong>
          {{ citationPreview.lineStart
          }}{{
            citationPreview.lineEnd != null && citationPreview.lineEnd !== citationPreview.lineStart
              ? '–' + citationPreview.lineEnd
              : ''
          }}
        </p>
        <p v-if="citationPreview.sourceUrl" class="cite-preview-meta">
          <strong>链接：</strong>
          <a :href="citationPreview.sourceUrl" target="_blank" rel="noopener noreferrer">{{
            citationPreview.sourceUrl
          }}</a>
        </p>
        <pre class="cite-preview-body">{{ citationPreview.excerpt || '' }}</pre>
      </template>
      <template #footer>
        <el-button @click="citationPreviewVisible = false">关闭</el-button>
        <el-button
          v-if="citationPreview?.excerpt"
          type="primary"
          @click="copyText(citationPreview.excerpt, '引用片段')"
        >
          复制片段
        </el-button>
      </template>
    </el-dialog>

    <TechLoadingOverlay
      :visible="importing || reindexBusy"
      :message="importing ? '正在抓取网页并入队解析…' : '正在同步向量索引…'"
    />

    <el-drawer v-model="docErrVisible" title="解析失败详情" size="40%">
      <p><strong>文件：</strong>{{ docErrItem?.filename || '-' }}</p>
      <p><strong>状态：</strong>{{ statusLabel(docErrItem?.status) }}</p>
      <el-input
        type="textarea"
        :rows="14"
        :model-value="docErrItem?.errorMessage || '无错误详情'"
        readonly
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import * as echarts from 'echarts'
import TechLoadingOverlay from '../components/TechLoadingOverlay.vue'
import { useTheme } from '../composables/useTheme'
import {
  initMermaidTheme,
  markdownToSafeHtml,
  runMermaidInContainer,
} from '../utils/richMarkdown'
import {
  Warning,
  ArrowLeft,
  QuestionFilled,
  Promotion,
  RefreshRight,
  Download,
  DocumentCopy,
  Tickets,
  Reading,
  FolderOpened,
  UploadFilled,
  Document,
} from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import http from '../api/http'
import { streamChatRequest } from '../api/chatStream'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const { currentId, themes: themeList } = useTheme()
const isUiDark = computed(() => {
  const t = themeList.find((x) => x.id === currentId.value)
  return !!(t && t.dark)
})
const kbId = computed(() => route.params.id)
const ragTab = ref('ask')
const docTableHeight = 400
const historyScrollHeight = 'auto'

const kb = ref(null)
const me = ref(null)
const isOwner = computed(() => kb.value && me.value && kb.value.ownerUserId === me.value.id)
const canWrite = computed(() => kb.value && (kb.value.role === 'OWNER' || kb.value.role === 'WRITE'))

const docs = ref([])
const docLoading = ref(false)
const pollTimer = ref(null)
const docKeyword = ref('')
const docStatusFilter = ref('ALL')
const docExtFilter = ref('')
const docTagFilter = ref('')
const docSortOrder = ref('DESC')
const docTableRef = ref(null)
/** 按 id 记录勾选，避免定时刷新替换行对象后勾选被清空 */
const selectedDocIds = ref([])
let restoringDocSelection = false
/** 列表数据替换或 loading 时表格会误报 selection-change([])，期间勿同步到 selectedDocIds */
let docListRefreshing = false
let docLoadInflight = 0

function docRowKey(row) {
  return row.id
}
const importUrl = ref('')
/** 网页抓取：默认仅正文；可选 images / videos */
const urlImportModes = ref(['text'])
const urlImportMaxImages = ref(20)
const importing = ref(false)
const reindexBusy = ref(false)
const docDonutRef = ref(null)
let docDonutChart = null
const docErrVisible = ref(false)
const docErrItem = ref(null)
const uploadOverwrite = ref(false)
const question = ref('')
const answer = ref('')
const citations = ref([])
/** 引用侧缩略图 blob URL，下标与 citations 对齐 */
const citationThumbUrls = ref({})
const citationPreviewImageUrl = ref('')
const lastMeta = ref(null)
const advOpen = ref([])
const chatSettings = reactive({
  contextChunks: 5,
  temperature: 0.7,
  topP: 0.9,
  topK: 40,
  answerStyle: 'NORMAL',
})
const suggestedQuestions = ref([])
const suggestTimer = ref(null)
const useStream = ref(true)
const currentHistoryId = ref(null)
const activeSessionId = ref(null)
const pendingNewSession = ref(false)
const lastSentMode = ref(null)
const lastAppliedQuestion = ref('')
const citationPreviewVisible = ref(false)
const citationPreview = ref(null)
const ctxCollapsed = ref(false)
const quickPrompts = [
  { label: '总结要点', text: '请阅读上下文，用要点列表总结关键信息与结论。' },
  { label: '提取步骤', text: '请从上下文中提取可执行的操作步骤，按顺序编号列出。' },
  { label: '对比差异', text: '请对比上下文中不同方案/配置的差异，用表格或条目说明。' },
  { label: '表格归纳', text: '若适合，请用 Markdown 表格归纳上下文中的结构化信息。' },
]

const mdAnswerRef = ref(null)
const renderedAnswerHtml = ref('')

watch(
  () => answer.value,
  async (val) => {
    if (!val) {
      renderedAnswerHtml.value = ''
      return
    }
    try {
      const withRefs = val.replace(/\[(\d+)\]/g, (_m, n) => `[${n}](#cite-${n})`)
      renderedAnswerHtml.value = markdownToSafeHtml(withRefs)
    } catch {
      renderedAnswerHtml.value = DOMPurify.sanitize(`<pre>${escapeHtml(val)}</pre>`)
    }
    await nextTick()
    await runMermaidInContainer(mdAnswerRef.value)
  },
  { immediate: true },
)

watch(isUiDark, (d) => initMermaidTheme(d), { immediate: true })

watch(
  () => question.value,
  (val) => {
    const normalized = (val || '').trim()
    if (!normalized) {
      return
    }
    if (lastAppliedQuestion.value && normalized !== lastAppliedQuestion.value) {
      pendingNewSession.value = true
      activeSessionId.value = null
    }
  },
)

function revokeCitationThumbBlobs() {
  const m = citationThumbUrls.value
  Object.keys(m).forEach((k) => {
    const u = m[k]
    if (u) URL.revokeObjectURL(u)
  })
  citationThumbUrls.value = {}
}

watch(
  () => citations.value,
  (list) => {
    revokeCitationThumbBlobs()
    if (!list || !list.length) return
    list.forEach((c, i) => {
      if (c.modality !== 'image' || !c.documentId) return
      http
        .get(`/documents/${c.documentId}/file`, { responseType: 'blob' })
        .then(({ data }) => {
          const url = URL.createObjectURL(data)
          citationThumbUrls.value = { ...citationThumbUrls.value, [i]: url }
        })
        .catch(() => {})
    })
  },
  { deep: true },
)

watch(citationPreviewVisible, (open) => {
  if (!open && citationPreviewImageUrl.value) {
    URL.revokeObjectURL(citationPreviewImageUrl.value)
    citationPreviewImageUrl.value = ''
  }
})

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function markQuestionAsFreshSession(text) {
  question.value = text
  const normalized = (text || '').trim()
  if (normalized && normalized !== lastAppliedQuestion.value) {
    pendingNewSession.value = true
    activeSessionId.value = null
  }
}

function applyQuickPrompt(qp) {
  markQuestionAsFreshSession(qp.text)
  loadSuggestions()
}

function clearQuestion() {
  question.value = ''
  lastAppliedQuestion.value = ''
  suggestedQuestions.value = []
}

function startNewChat() {
  pendingNewSession.value = true
  activeSessionId.value = null
  lastSentMode.value = null
  question.value = ''
  lastAppliedQuestion.value = ''
  answer.value = ''
  citations.value = []
  lastMeta.value = null
  currentHistoryId.value = null
  ragTab.value = 'ask'
  ElMessage.success('已开启新对话，下一条提问将使用新会话上下文')
}

function citationTagLabel(c, i) {
  const name = (c.documentName || '文档').replace(/\s+/g, ' ')
  const short = name.length > 14 ? `${name.slice(0, 14)}…` : name
  const pic = c.modality === 'image' ? '🖼 ' : ''
  return `[${i + 1}] ${pic}${short}`
}

async function openCitationPreview(c) {
  if (citationPreviewImageUrl.value) {
    URL.revokeObjectURL(citationPreviewImageUrl.value)
    citationPreviewImageUrl.value = ''
  }
  citationPreview.value = c ? { ...c } : null
  citationPreviewVisible.value = true
  if (c?.modality === 'image' && c.documentId) {
    try {
      const { data } = await http.get(`/documents/${c.documentId}/file`, { responseType: 'blob' })
      citationPreviewImageUrl.value = URL.createObjectURL(data)
    } catch {
      ElMessage.warning('图片预览加载失败')
    }
  }
}

function historyHeading(item) {
  const t = (item.sessionTitle || '').trim()
  if (t) return t
  const q = item.question || ''
  return q.length > 36 ? `${q.slice(0, 36)}…` : q
}

async function copyRenderedMarkdown() {
  await copyText(answer.value, 'Markdown 源码')
}

function onAnswerClick(event) {
  const anchor = event.target?.closest?.('a')
  if (!anchor) return
  const href = anchor.getAttribute('href') || ''
  if (!href.startsWith('#cite-')) return
  event.preventDefault()
  const el = document.getElementById(href.slice(1))
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.remove('cite-flash')
  requestAnimationFrame(() => el.classList.add('cite-flash'))
}
const chatHistory = ref([])
const chatLoading = ref(false)
const historyKeyword = ref('')
const historySearchTimer = ref(null)
const historyPage = ref(0)
const historyPageUi = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)

const memberVisible = ref(false)
const members = ref([])
const memberLoading = ref(false)
const newMemberUsername = ref('')
const newMemberPerm = ref('READ')
const kbEditVisible = ref(false)
const kbEditSaving = ref(false)
const kbEditForm = ref({ name: '', description: '' })

const hasRunningDocs = computed(() =>
  docs.value.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING')
)
const hasReadyDocs = computed(() => docs.value.some((d) => d.status === 'READY'))
const canAsk = computed(() => hasReadyDocs.value)
const nextChatSessionMode = computed(() => {
  const isContinuation = !pendingNewSession.value && !!activeSessionId.value
  if (!isContinuation) {
    return { label: '下一次：新会话', type: 'success', isContinuation: false }
  }
  return { label: '下一次：续问模式', type: 'warning', isContinuation: true }
})
const lastChatSessionMode = computed(() => {
  if (lastSentMode.value === 'fresh') {
    return { label: '本次发送：新会话', type: 'success' }
  }
  if (lastSentMode.value === 'continuation') {
    return { label: '本次发送：续问模式', type: 'warning' }
  }
  return null
})
const askDisabledHint = computed(() => {
  if (hasRunningDocs.value) return '当前文档仍在解析中，请稍后再提问'
  if (docs.value.length === 0) return '请先上传至少一个文档并完成解析'
  return '暂无可用文档（请重试解析失败文档）'
})
const filteredHistory = computed(() => {
  return chatHistory.value
})
const docMetricItems = computed(() => {
  const d = docs.value
  const total = d.length
  const ready = d.filter((x) => x.status === 'READY').length
  const running = d.filter((x) => x.status === 'PENDING' || x.status === 'PROCESSING').length
  const failed = d.filter((x) => x.status === 'FAILED').length
  return [
    { key: 'total', label: '全部', value: total },
    { key: 'ready', label: '可用', value: ready },
    { key: 'running', label: '解析中', value: running },
    { key: 'failed', label: '失败', value: failed },
  ]
})
const docReadyPercent = computed(() => {
  if (!docs.value.length) return 0
  const ready = docs.value.filter((x) => x.status === 'READY').length
  return Math.round((ready * 100) / docs.value.length)
})

const filteredDocs = computed(() => {
  const kw = docKeyword.value.trim().toLowerCase()
  let arr = docs.value.filter((d) => {
    const byName = !kw || (d.filename || '').toLowerCase().includes(kw)
    const byStatus = docStatusFilter.value === 'ALL' || d.status === docStatusFilter.value
    return byName && byStatus
  })
  arr = [...arr].sort((a, b) => {
    const ta = new Date(a.createdAt || 0).getTime()
    const tb = new Date(b.createdAt || 0).getTime()
    return docSortOrder.value === 'ASC' ? ta - tb : tb - ta
  })
  return arr
})

watch(filteredDocs, () => {
  nextTick(() => {
    void restoreDocTableSelection()
  })
})

async function loadKbAndMe() {
  const [{ data: k }, { data: u }] = await Promise.all([
    http.get(`/knowledge-bases/${kbId.value}`),
    http.get('/users/me'),
  ])
  kb.value = k
  me.value = u
}

/**
 * @param {boolean} silent 为 true 时不显示全表 loading（轮询用），避免遮罩触发表格重绘导致勾选被清空
 */
async function loadDocs(silent = false) {
  docLoadInflight += 1
  docListRefreshing = true
  if (!silent) docLoading.value = true
  try {
    const { data } = await http.get(`/knowledge-bases/${kbId.value}/documents`, {
      params: {
        status: docStatusFilter.value,
        ext: docExtFilter.value || undefined,
        tag: docTagFilter.value.trim() || undefined,
        sort: docSortOrder.value,
      },
    })
    const list = data || []
    docs.value = list
    const idSet = new Set(list.map((d) => d.id))
    selectedDocIds.value = selectedDocIds.value.filter((id) => idSet.has(id))
    syncPolling()
    renderDocDonut()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    if (!silent) docLoading.value = false
  }
  try {
    await nextTick()
    await restoreDocTableSelection()
  } finally {
    docLoadInflight -= 1
    if (docLoadInflight === 0) docListRefreshing = false
  }
}

/** 同步表格勾选与 selectedDocIds；返回在内部 selection 事件消化完成后再 resolve 的 Promise */
function restoreDocTableSelection() {
  const table = docTableRef.value
  if (!table || !canWrite.value) {
    return Promise.resolve()
  }
  const ids = new Set(selectedDocIds.value)
  restoringDocSelection = true
  return new Promise((resolve) => {
    try {
      if (ids.size === 0) {
        table.clearSelection()
      } else {
        for (const row of filteredDocs.value) {
          table.toggleRowSelection(row, ids.has(row.id))
        }
      }
    } finally {
      nextTick(() => {
        nextTick(() => {
          restoringDocSelection = false
          resolve()
        })
      })
    }
  })
}

function startPolling() {
  if (pollTimer.value) return
  pollTimer.value = setInterval(() => {
    loadDocs(true)
  }, 3000)
}

function stopPolling() {
  if (!pollTimer.value) return
  clearInterval(pollTimer.value)
  pollTimer.value = null
}

function syncPolling() {
  if (hasRunningDocs.value) startPolling()
  else stopPolling()
}

function statusLabel(status) {
  switch (status) {
    case 'PENDING':
      return '待处理'
    case 'PROCESSING':
      return '解析中'
    case 'READY':
      return '可用'
    case 'FAILED':
      return '失败'
    default:
      return status || '-'
  }
}

function statusTagType(status) {
  switch (status) {
    case 'READY':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
      return 'primary'
    default:
      return 'info'
  }
}

function stageLabel(status) {
  switch (status) {
    case 'PENDING':
      return '上传完成'
    case 'PROCESSING':
      return '切分/嵌入中'
    case 'READY':
      return '已入库可检索'
    case 'FAILED':
      return '处理失败'
    default:
      return '未知阶段'
  }
}

function beforeUpload() {
  return true
}

async function handleCustomUpload(option) {
  const form = new FormData()
  form.append('file', option.file)
  form.append('overwrite', String(uploadOverwrite.value))
  try {
    await http.post(`/knowledge-bases/${kbId.value}/documents`, form, {
      onUploadProgress: (evt) => {
        if (!evt?.total) return
        option.onProgress?.({ percent: Math.round((evt.loaded * 100) / evt.total) })
      },
    })
    option.onSuccess?.({})
    ElMessage.success(`${option.file.name} 上传成功，已进入解析队列`)
    setTimeout(loadDocs, 800)
  } catch (e) {
    option.onError?.(e)
    ElMessage.error(`${option.file.name} 上传失败：${e.message}`)
  }
}

function onDocSelection(rows) {
  if (restoringDocSelection || docListRefreshing) return
  if (docLoading.value) return
  selectedDocIds.value = (rows || []).map((r) => r.id)
}

function clearDocSelection() {
  selectedDocIds.value = []
  docTableRef.value?.clearSelection()
}

async function batchDeleteDocs() {
  if (!selectedDocIds.value.length) return
  await ElMessageBox.confirm(`确定批量删除 ${selectedDocIds.value.length} 个文档？`, '提示')
  await http.post('/documents/batch-delete', {
    ids: [...selectedDocIds.value],
  })
  clearDocSelection()
  ElMessage.success('已批量删除')
  await loadDocs()
}

function canPreview(row) {
  const name = (row?.filename || '').toLowerCase()
  return (
    name.endsWith('.pdf') ||
    name.endsWith('.md') ||
    name.endsWith('.txt') ||
    name.endsWith('.markdown') ||
    name.endsWith('.png') ||
    name.endsWith('.jpg') ||
    name.endsWith('.jpeg') ||
    name.endsWith('.webp') ||
    name.endsWith('.gif') ||
    name.endsWith('.mp4') ||
    name.endsWith('.mov') ||
    name.endsWith('.m4v') ||
    name.endsWith('.webm') ||
    name.endsWith('.avi')
  )
}

function openPreview(row) {
  window.open(`/api/v1/documents/${row.id}/file`, '_blank')
}

async function openTagDialog(row) {
  const initial = (row.tags || []).join(', ')
  const value = await ElMessageBox.prompt('输入标签，多个用英文逗号分隔', '文档标签', {
    inputValue: initial,
    confirmButtonText: '保存',
    cancelButtonText: '取消',
  })
    .then((r) => r.value || '')
    .catch(() => null)
  if (value == null) return
  const tags = value
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
  await http.patch(`/documents/${row.id}/tags`, { tags })
  ElMessage.success('标签已更新')
  await loadDocs()
}

async function submitUrlImport() {
  const url = importUrl.value.trim()
  if (!url) return
  const modes = urlImportModes.value || []
  if (modes.length === 0) {
    ElMessage.warning('请至少选择一种抓取内容')
    return
  }
  importing.value = true
  try {
    const { data } = await http.post(`/knowledge-bases/${kbId.value}/documents/import-url`, {
      url,
      overwrite: uploadOverwrite.value,
      scrapeText: modes.includes('text'),
      scrapeImages: modes.includes('images'),
      scrapeVideoLinks: modes.includes('videos'),
      maxImages: urlImportMaxImages.value,
    })
    const n = Array.isArray(data) ? data.length : 1
    importUrl.value = ''
    ElMessage.success(`已创建 ${n} 个文档并入队解析`)
    await loadDocs()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    importing.value = false
  }
}

async function removeDoc(row) {
  await ElMessageBox.confirm('确定删除该文档？', '提示')
  await http.delete(`/documents/${row.id}`)
  answer.value = ''
  citations.value = []
  lastMeta.value = null
  currentHistoryId.value = null
  lastSentMode.value = null
  pendingNewSession.value = true
  activeSessionId.value = null
  ElMessage.success('已删除')
  loadDocs()
}

async function reindexDoc(row) {
  const actionText = row.status === 'FAILED' ? '重试解析' : '重建索引'
  await ElMessageBox.confirm(
    `${actionText}将重新处理该文档并覆盖原有向量索引，是否继续？`,
    '确认操作'
  )
  reindexBusy.value = true
  try {
    await http.post(`/documents/${row.id}/reindex`)
    ElMessage.success(`已触发${actionText}`)
    await loadDocs()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    reindexBusy.value = false
  }
}

async function ask() {
  if (chatLoading.value) return
  if (!canAsk.value) {
    ElMessage.warning(askDisabledHint.value)
    return
  }
  if (!question.value.trim()) return
  lastSentMode.value = pendingNewSession.value || !activeSessionId.value ? 'fresh' : 'continuation'
  chatLoading.value = true
  answer.value = ''
  citations.value = []
  lastMeta.value = null
  try {
    currentHistoryId.value = null
    const payload = {
      knowledgeBaseId: kbId.value,
      question: question.value.trim(),
      contextChunks: chatSettings.contextChunks,
      temperature: chatSettings.temperature,
      topP: chatSettings.topP,
      topK: chatSettings.topK,
      answerStyle: chatSettings.answerStyle,
      sessionId: activeSessionId.value || undefined,
      newSession: pendingNewSession.value ? true : undefined,
    }
    if (useStream.value) {
      let streamSurface = false
      let pending = ''
      let rafId = null
      const flushStreamBuffer = () => {
        if (pending.length) {
          answer.value += pending
          pending = ''
        }
        rafId = null
      }
      await streamChatRequest(payload, {
        onToken: (chunk) => {
          if (chunk != null && String(chunk).length > 0 && !streamSurface) {
            streamSurface = true
            chatLoading.value = false
          }
          pending += chunk
          if (rafId == null) {
            rafId = requestAnimationFrame(flushStreamBuffer)
          }
        },
        onDone: (done) => {
          if (rafId != null) {
            cancelAnimationFrame(rafId)
            rafId = null
          }
          flushStreamBuffer()
          citations.value = done.citations || []
          lastMeta.value = {
            retrievedCandidates: done.retrievedCandidates ?? 0,
            contextChunksUsed: done.contextChunksUsed ?? 0,
            latencyMs: done.latencyMs ?? 0,
          }
          currentHistoryId.value = done.historyId || null
          if (done.sessionId) activeSessionId.value = done.sessionId
          pendingNewSession.value = false
          if (!streamSurface) chatLoading.value = false
        },
      })
    } else {
      const { data } = await http.post('/chat', payload)
      answer.value = data.answer
      citations.value = data.citations || []
      lastMeta.value = {
        retrievedCandidates: data.retrievedCandidates ?? 0,
        contextChunksUsed: data.contextChunksUsed ?? 0,
        latencyMs: data.latencyMs ?? 0,
      }
      if (data.sessionId) activeSessionId.value = data.sessionId
      pendingNewSession.value = false
    }
    lastAppliedQuestion.value = question.value.trim()
    suggestedQuestions.value = []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    chatLoading.value = false
  }
  try {
    await loadChatHistory()
  } catch {
    /* 侧边历史加载失败不影响本次问答展示 */
  }
}

async function feedbackCurrent(helpful) {
  if (!currentHistoryId.value) {
    ElMessage.warning('请先通过本页生成一次回答')
    return
  }
  await http.post(
    `/chat/history/${currentHistoryId.value}/feedback`,
    { helpful },
    { params: { knowledgeBaseId: kbId.value } },
  )
  ElMessage.success('反馈已提交')
}

function renderDocDonut() {
  if (!docDonutRef.value) return
  if (!docDonutChart) {
    docDonutChart = echarts.init(docDonutRef.value)
  }
  const ready = docs.value.filter((x) => x.status === 'READY').length
  const running = docs.value.filter((x) => x.status === 'PENDING' || x.status === 'PROCESSING').length
  const failed = docs.value.filter((x) => x.status === 'FAILED').length
  docDonutChart.setOption({
    animationDuration: 400,
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['62%', '82%'],
        label: { show: false },
        data: [
          { value: ready, name: '可用' },
          { value: running, name: '解析中' },
          { value: failed, name: '失败' },
        ],
      },
    ],
  })
}

async function loadSuggestions() {
  try {
    const { data } = await http.get('/chat/suggestions', {
      params: {
        knowledgeBaseId: kbId.value,
        q: question.value.trim() || undefined,
      },
    })
    suggestedQuestions.value = data?.suggestions || []
  } catch {
    suggestedQuestions.value = []
  }
}

function useSuggestion(s) {
  markQuestionAsFreshSession(s)
}

async function loadChatHistory() {
  try {
    const { data } = await http.get('/chat/history', {
      params: {
        knowledgeBaseId: kbId.value,
        keyword: historyKeyword.value.trim() || undefined,
        page: historyPage.value,
        size: historySize.value,
      },
    })
    chatHistory.value = data?.content || []
    historyTotal.value = data?.totalElements ?? 0
    historyPage.value = data?.number ?? historyPage.value
    historyPageUi.value = historyPage.value + 1
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openKbEditDialog() {
  kbEditForm.value = {
    name: kb.value?.name || '',
    description: kb.value?.description || '',
  }
  kbEditVisible.value = true
}

async function saveKbEdit() {
  kbEditSaving.value = true
  try {
    const { data } = await http.put(`/knowledge-bases/${kbId.value}`, kbEditForm.value)
    kb.value = data
    kbEditVisible.value = false
    ElMessage.success('知识库信息已更新')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    kbEditSaving.value = false
  }
}

function formatTime(v) {
  if (!v) return ''
  try {
    return new Date(v).toLocaleString()
  } catch {
    return v
  }
}

function applyHistory(item) {
  question.value = item.question || ''
  lastAppliedQuestion.value = (item.question || '').trim()
  answer.value = item.answer || ''
  citations.value = item.citations || []
  lastMeta.value = null
  activeSessionId.value = item.sessionId || null
  pendingNewSession.value = false
  ElMessage.success('已回填历史问答')
}

function applyHistorySwitchTab(item) {
  applyHistory(item)
  ragTab.value = 'ask'
}

async function deleteHistory(item) {
  await ElMessageBox.confirm('确定删除这条历史记录？', '提示')
  await http.delete(`/chat/history/${item.id}`, {
    params: { knowledgeBaseId: kbId.value },
  })
  if (answer.value === item.answer && question.value === item.question) {
    answer.value = ''
    citations.value = []
  }
  ElMessage.success('已删除历史记录')
  await loadChatHistory()
}

async function feedbackHistory(item, helpful) {
  try {
    let note = ''
    if (helpful === false) {
      note = await ElMessageBox.prompt('可选：填写无用原因，便于系统优化', '反馈', {
        confirmButtonText: '提交',
        cancelButtonText: '跳过',
        inputPlaceholder: '例如：引用不准确 / 答非所问',
      })
        .then((r) => r.value || '')
        .catch(() => '')
    }
    await http.post(`/chat/history/${item.id}/feedback`, {
      helpful,
      note: note || undefined,
    }, {
      params: { knowledgeBaseId: kbId.value },
    })
    item.helpful = helpful
    item.feedbackNote = note || item.feedbackNote
    ElMessage.success('反馈已记录')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function clearHistory() {
  await ElMessageBox.confirm('确定清空当前知识库的全部历史记录？', '提示')
  await http.delete('/chat/history', {
    params: { knowledgeBaseId: kbId.value },
  })
  chatHistory.value = []
  historyKeyword.value = ''
  historyPage.value = 0
  historyPageUi.value = 1
  activeSessionId.value = null
  pendingNewSession.value = false
  historyTotal.value = 0
  answer.value = ''
  citations.value = []
  lastMeta.value = null
  ElMessage.success('已清空历史记录')
}

function onHistorySizeChange() {
  historyPage.value = 0
  historyPageUi.value = 1
  loadChatHistory()
}

async function copyText(text, label) {
  const value = (text || '').trim()
  if (!value) {
    ElMessage.warning(`没有可复制的${label}`)
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`已复制${label}`)
  } catch {
    ElMessage.error(`复制${label}失败`)
  }
}

function buildMarkdown() {
  const lines = []
  lines.push(`# 知识库问答导出`)
  lines.push('')
  lines.push(`- 知识库：${kb.value?.name || '-'}`)
  lines.push(`- 导出时间：${new Date().toLocaleString()}`)
  if (lastMeta.value) {
    lines.push(
      `- 检索命中：${lastMeta.value.retrievedCandidates} 条 · 使用片段：${lastMeta.value.contextChunksUsed} 条 · 耗时：${lastMeta.value.latencyMs} ms`,
    )
  }
  lines.push('')
  lines.push('## 问题')
  lines.push('')
  lines.push(question.value || '-')
  lines.push('')
  lines.push('## 回答')
  lines.push('')
  lines.push(answer.value || '-')
  lines.push('')
  lines.push('## 引用')
  lines.push('')
  if (!citations.value?.length) {
    lines.push('- 无')
  } else {
    citations.value.forEach((c, i) => {
      const page = c.pageNo ? ` 第${c.pageNo}页` : ''
      const linesMeta =
        c.lineStart != null
          ? ` 行${c.lineStart}${c.lineEnd != null && c.lineEnd !== c.lineStart ? '–' + c.lineEnd : ''}`
          : ''
      const url = c.sourceUrl ? ` ${c.sourceUrl}` : ''
      lines.push(`- [${i + 1}] ${c.documentName || '-'}${page}${linesMeta}${url}`)
      lines.push('')
      lines.push('```text')
      lines.push((c.excerpt || '').trim())
      lines.push('```')
    })
  }
  lines.push('')
  return lines.join('\n')
}

function exportCurrentQa() {
  if (!answer.value) {
    ElMessage.warning('当前没有可导出的回答')
    return
  }
  const content = buildMarkdown()
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const kbName = (kb.value?.name || 'knowledge').replace(/[\\/:*?"<>|]/g, '_')
  a.href = url
  a.download = `${kbName}-qa-${Date.now()}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出 Markdown')
}

function openDocError(row) {
  docErrItem.value = row
  docErrVisible.value = true
}

function openMemberDialog() {
  memberVisible.value = true
}

async function loadMembers() {
  if (!isOwner.value) return
  memberLoading.value = true
  try {
    const { data } = await http.get(`/knowledge-bases/${kbId.value}/members`)
    members.value = data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    memberLoading.value = false
  }
}

async function addMember() {
  if (!newMemberUsername.value.trim()) return
  try {
    await http.post(`/knowledge-bases/${kbId.value}/members`, {
      username: newMemberUsername.value.trim(),
      permission: newMemberPerm.value,
    })
    ElMessage.success('已保存')
    newMemberUsername.value = ''
    await loadMembers()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function removeMember(row) {
  await ElMessageBox.confirm(`移除成员 ${row.username}？`, '提示')
  try {
    await http.delete(`/knowledge-bases/${kbId.value}/members/${row.userId}`)
    ElMessage.success('已移除')
    await loadMembers()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  try {
    await loadKbAndMe()
    await loadDocs()
    await loadChatHistory()
    await loadSuggestions()
  } catch (e) {
    ElMessage.error(e.message)
  }
})

watch(historyKeyword, () => {
  historyPage.value = 0
  historyPageUi.value = 1
  if (historySearchTimer.value) clearTimeout(historySearchTimer.value)
  historySearchTimer.value = setTimeout(() => {
    loadChatHistory()
  }, 250)
})

watch(question, () => {
  if (suggestTimer.value) clearTimeout(suggestTimer.value)
  suggestTimer.value = setTimeout(() => {
    loadSuggestions()
  }, 260)
})

watch(historyPageUi, (v) => {
  historyPage.value = Math.max(0, (v || 1) - 1)
})

watch(kbId, async () => {
  activeSessionId.value = null
  pendingNewSession.value = false
  answer.value = ''
  citations.value = []
  currentHistoryId.value = null
  lastMeta.value = null
  try {
    await loadKbAndMe()
    await loadDocs()
    await loadChatHistory()
  } catch (e) {
    ElMessage.error(e.message)
  }
})

onUnmounted(() => {
  stopPolling()
  if (historySearchTimer.value) clearTimeout(historySearchTimer.value)
  if (suggestTimer.value) clearTimeout(suggestTimer.value)
  revokeCitationThumbBlobs()
  if (citationPreviewImageUrl.value) {
    URL.revokeObjectURL(citationPreviewImageUrl.value)
    citationPreviewImageUrl.value = ''
  }
})
</script>

<style scoped>
.shell {
  animation: rag-page-in 0.55s ease both;
}

@keyframes rag-page-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.page {
  max-width: 1320px;
  margin: 0 auto;
  padding: 24px 20px 40px;
}

.kb-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 1180px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }
}

.doc-card,
.rag-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.doc-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.rag-card :deep(.el-card__body) {
  display: block;
}

.doc-card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.doc-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.doc-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
  font-size: 15px;
}

.doc-head-ico {
  font-size: 20px;
  color: var(--app-accent);
}

.doc-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.doc-progress-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-progress-wrap :deep(.el-progress) {
  flex: 1;
}

.doc-progress-text {
  font-size: 12px;
  color: var(--app-text-muted);
  white-space: nowrap;
}

.metric {
  padding: 10px 8px;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface-2) 92%, transparent);
  text-align: center;
}

.metric-val {
  display: block;
  font-size: 1.28rem;
  font-weight: 750;
  line-height: 1.2;
  color: var(--app-text);
}

.metric-label {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--app-text-muted);
  letter-spacing: 0.06em;
}

.upload-hero {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-drag {
  width: 100%;
}

.upload-drag :deep(.el-upload) {
  width: 100%;
}

.upload-drag :deep(.el-upload-dragger) {
  width: 100%;
  padding: 22px 16px;
  background: color-mix(in srgb, var(--app-surface-2) 90%, transparent);
  border-color: color-mix(in srgb, var(--app-accent) 28%, var(--app-border));
  border-radius: 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.upload-drag :deep(.el-upload-dragger:hover) {
  border-color: color-mix(in srgb, var(--app-accent) 55%, var(--app-border));
  box-shadow: 0 10px 28px color-mix(in srgb, var(--app-accent) 12%, transparent);
}

.upload-ico {
  font-size: 34px;
  color: var(--app-accent);
  margin-bottom: 6px;
}

.upload-copy .upload-title {
  margin: 0;
  font-weight: 650;
  font-size: 14px;
  color: var(--app-text);
}

.upload-copy .upload-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.upload-overwrite {
  font-size: 12px;
  color: var(--app-text-muted);
}

.doc-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.doc-tool-search {
  flex: 1;
  min-width: 160px;
  max-width: 260px;
}

.doc-tool-select {
  width: 130px;
}

.doc-table-wrap {
  flex: 1;
  min-height: 240px;
}

.doc-empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 12px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.doc-empty-hint .el-icon {
  font-size: 26px;
  opacity: 0.75;
}

.rag-tabs {
  display: block;
}

.rag-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.rag-tabs :deep(.el-tabs__content),
.rag-tabs :deep(.el-tab-pane) {
  display: block;
  min-height: auto;
}

.rag-tab-scroll {
  max-height: min(70vh, 700px);
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-gutter: stable;
}

.rag-split {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: stretch;
}

.rag-split-main {
  min-width: 0;
}

.answer-panel {
  margin-top: 16px;
}

.answer-scroll {
  max-height: none;
  overflow: visible;
  padding-right: 0;
}

.ctx-aside {
  position: static;
  min-width: 0;
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--app-surface-2) 90%, transparent);
}

.ctx-aside.collapsed {
  position: static;
}

.ctx-aside-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--app-border);
}

.ctx-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text);
}

.ctx-scroll {
  padding: 10px 12px 12px;
}

.source-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--app-border);
}

.source-tags-label {
  font-size: 12px;
  color: var(--app-text-muted);
}

.source-tag {
  cursor: pointer;
}

.history-q-sub {
  margin: -4px 0 8px;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.4;
}

.cite-preview-meta {
  margin: 0 0 8px;
  font-size: 13px;
  word-break: break-all;
}

.cite-preview-body {
  white-space: pre-wrap;
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--app-surface) 80%, var(--app-border));
  border: 1px solid var(--app-border);
  font-size: 13px;
  max-height: 280px;
  overflow: auto;
}

.history-tab {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: none;
}

.history-search {
  width: 100%;
}

.bar {
  display: grid;
  grid-template-columns: minmax(120px, auto) 1fr minmax(180px, auto);
  align-items: center;
  margin-bottom: 22px;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  backdrop-filter: blur(16px);
  box-shadow: var(--app-shadow);
}

.bar-center {
  text-align: center;
}

.bar-center h2 {
  margin: 0;
  font-size: 1.28rem;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--app-text);
}

.kb-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-muted);
  line-height: 1.45;
}

.bar-right {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 32px;
}
.answer {
  margin-top: 16px;
}
.doc-tools {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.chat-actions {
  margin-top: 12px;
  display: inline-flex;
  gap: 8px;
}
.question-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}
.answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.answer-head h4 {
  margin: 0;
}
.answer-actions {
  display: inline-flex;
  gap: 6px;
}
.history-title {
  margin: 0;
  font-size: 14px;
}
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.history-pager {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.history-empty {
  color: var(--app-text-muted);
  font-size: 13px;
  padding: 6px 0;
}
.history-item {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: var(--app-surface-2);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}
.history-item:hover {
  border-color: color-mix(in srgb, var(--app-accent) 55%, var(--app-border));
  box-shadow: 0 12px 32px color-mix(in srgb, var(--app-accent) 14%, transparent);
  transform: translateY(-1px);
}
.history-q,
.history-a {
  margin: 0 0 6px;
  font-size: 13px;
  white-space: pre-wrap;
}
.history-time {
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
}
.history-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.history-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.cite {
  margin-bottom: 12px;
  font-size: 13px;
}
.cite pre {
  white-space: pre-wrap;
  background: color-mix(in srgb, var(--app-surface) 80%, var(--app-border));
  padding: 8px;
  border-radius: 8px;
  margin: 6px 0 0;
  border: 1px solid var(--app-border);
}
.hint {
  font-size: 12px;
  color: var(--app-text-muted);
  margin: 0;
}
.err-icon {
  margin-left: 6px;
  color: #e6a23c;
  vertical-align: middle;
}

.rag-card :deep(.el-card__header) {
  padding: 14px 18px;
}
.rag-card-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rag-title-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.rag-h {
  font-size: 1.05rem;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--app-text);
}
.rag-title-hint {
  font-size: 12px;
  color: var(--app-text-muted);
}
.rag-alert {
  margin-bottom: 12px;
}
.quick-prompts {
  margin-bottom: 12px;
}
.quick-label {
  display: block;
  font-size: 12px;
  color: var(--app-text-muted);
  margin-bottom: 8px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.q-area :deep(textarea) {
  font-size: 14px;
  line-height: 1.55;
}
.q-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.adv-collapse {
  margin: 14px 0 6px;
  border: none;
  --el-collapse-border-color: transparent;
}
.adv-collapse :deep(.el-collapse-item__header) {
  font-weight: 600;
  color: var(--app-text);
  padding-left: 0;
}
.adv-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}
.adv-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.adv-item.full {
  width: 100%;
}
.adv-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--app-text-muted);
  margin-bottom: 6px;
}
.adv-tip {
  cursor: help;
  color: var(--app-accent);
}
.chat-actions-main {
  margin-top: 14px;
  flex-wrap: wrap;
}
.btn-gen {
  font-weight: 600;
  background: linear-gradient(120deg, var(--app-accent), var(--app-accent-2)) !important;
  border: none !important;
  box-shadow: 0 12px 32px color-mix(in srgb, var(--app-accent) 30%, transparent);
}
.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.answer-loading {
  margin-top: 16px;
  padding: 8px 0;
}
.loading-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
  text-align: center;
}
.answer-head-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.answer-badge {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--app-accent);
}
.answer-hint {
  font-size: 11px;
  color: var(--app-text-muted);
}
.answer .answer-head {
  align-items: flex-start;
}
.answer .answer-actions {
  flex-wrap: wrap;
}
.md-body {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface-2) 92%, transparent);
  font-size: 14px;
  line-height: 1.65;
  color: var(--app-text);
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 1em 0 0.45em;
  font-weight: 650;
  line-height: 1.35;
}
.md-body :deep(h2) {
  font-size: 1.08rem;
  padding-left: 10px;
  border-left: 3px solid var(--app-accent);
}
.md-body :deep(p) {
  margin: 0.5em 0;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.35em;
}
.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin: 0.75em 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid var(--app-border);
  padding: 6px 10px;
}
.md-body :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.88em;
  padding: 2px 6px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--app-accent) 12%, var(--app-surface));
}
.md-body :deep(pre) {
  margin: 0.6em 0;
  padding: 12px;
  border-radius: 10px;
  overflow: auto;
  background: color-mix(in srgb, var(--app-surface) 70%, #00000008);
  border: 1px solid var(--app-border);
}
.md-body :deep(pre code) {
  background: none;
  padding: 0;
}
.md-body :deep(blockquote) {
  margin: 0.6em 0;
  padding: 8px 12px;
  border-left: 3px solid var(--app-accent-2);
  color: var(--app-text-muted);
  background: color-mix(in srgb, var(--app-surface) 88%, transparent);
  border-radius: 0 8px 8px 0;
}

.md-body :deep(a[href^='#cite-']) {
  color: var(--app-accent);
  font-weight: 700;
  text-decoration: none;
  border-bottom: 1px dashed color-mix(in srgb, var(--app-accent) 50%, transparent);
}

.md-body :deep(a[href^='#cite-']:hover) {
  color: var(--app-accent-2);
}

.md-body :deep(.katex) {
  font-size: 1.05em;
}

.md-body :deep(.katex-display) {
  margin: 0.85em 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.md-body :deep(pre.mermaid) {
  background: color-mix(in srgb, var(--app-surface) 75%, var(--app-accent) 6%);
  border: 1px dashed color-mix(in srgb, var(--app-accent) 35%, var(--app-border));
  min-height: 120px;
}

.md-body :deep(pre.mermaid svg) {
  max-width: 100%;
}

.citations-block {
  margin-top: 18px;
}
.citations-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 10px;
  color: var(--app-text);
}
.citation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cite-card {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--app-surface-2);
}
.cite-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.cite-idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  background: color-mix(in srgb, var(--app-accent) 22%, transparent);
  color: var(--app-text);
}
.cite-name {
  font-weight: 600;
  flex: 1;
  min-width: 120px;
}
.cite-thumb-wrap {
  margin: 0 0 8px;
}
.cite-thumb {
  max-width: 100%;
  max-height: 160px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface) 88%, transparent);
}
.cite-preview-img-wrap {
  margin-bottom: 12px;
  text-align: center;
}
.cite-preview-img {
  max-width: 100%;
  max-height: 360px;
  border-radius: 8px;
  object-fit: contain;
  border: 1px solid var(--app-border);
}
.cite-body {
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-muted);
  background: transparent;
  border: none;
  padding: 0;
}

.cite-flash {
  animation: citeFlash 1s ease;
}

@keyframes citeFlash {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--app-accent) 45%, transparent);
    border-color: color-mix(in srgb, var(--app-accent) 65%, var(--app-border));
  }
  100% {
    box-shadow: none;
    border-color: var(--app-border);
  }
}

.history-rate {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 4px;
}

.url-import {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  margin-top: 10px;
}
.url-import-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.url-import-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-muted);
  flex-shrink: 0;
}
.url-import-input {
  flex: 1;
  min-width: 200px;
}
.url-import-options {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  font-size: 13px;
}
.url-import-opt-label {
  color: var(--app-text-muted);
  margin-right: 4px;
}
.url-import-maximg {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text-muted);
}
.url-import-hint {
  width: 100%;
  margin: 0;
  font-size: 12px;
  color: var(--app-text-muted);
  line-height: 1.45;
}

.q-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.session-mode-tag {
  margin-left: auto;
}

.session-mode-alert {
  margin-top: 10px;
}
</style>
