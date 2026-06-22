<template>
  <div class="p-4 sm:p-6 max-w-5xl mx-auto space-y-5">
    <PageHeader title="Help & Support" description="Documentation, community, and where to get help." />

    <div
      v-if="auth.isDemo"
      class="px-3 py-2 rounded-lg callout-warn text-sm"
    >
      You're exploring a read-only demo. Join the Discord below if you'd like to run your own dashboard.
    </div>

    <div class="grid sm:grid-cols-2 gap-4">
      <a
        :href="WIKI_URL" target="_blank" rel="noopener noreferrer"
        class="card flex items-start gap-3 hover:border-edge2 transition-colors group"
      >
        <span class="w-10 h-10 rounded-lg bg-brand-subtle text-brand flex items-center justify-center flex-shrink-0">
          <BookOpenIcon class="w-5 h-5" />
        </span>
        <div class="min-w-0">
          <h3 class="flex items-center gap-1">Wiki & Documentation <ArrowTopRightOnSquareIcon class="w-3.5 h-3.5 text-muted group-hover:text-brand" /></h3>
          <p class="text-sm text-muted mt-0.5">Setup guides, configuration reference, and how-tos for the dashboard.</p>
        </div>
      </a>

      <a
        :href="DISCORD_URL" target="_blank" rel="noopener noreferrer"
        class="card flex items-start gap-3 hover:border-edge2 transition-colors group"
      >
        <span class="w-10 h-10 rounded-lg bg-brand-subtle text-brand flex items-center justify-center flex-shrink-0">
          <ChatBubbleLeftRightIcon class="w-5 h-5" />
        </span>
        <div class="min-w-0">
          <h3 class="flex items-center gap-1">Support Discord <ArrowTopRightOnSquareIcon class="w-3.5 h-3.5 text-muted group-hover:text-brand" /></h3>
          <p class="text-sm text-muted mt-0.5">Ask questions, report issues, and chat with the community and developers.</p>
        </div>
      </a>
    </div>
  </div>
</template>

<script setup>
import PageHeader from '../components/ui/PageHeader.vue'
import { useAuthStore } from '../stores/auth'
import { WIKI_URL, DISCORD_URL } from '../support'
import { BookOpenIcon, ChatBubbleLeftRightIcon, ArrowTopRightOnSquareIcon } from '@heroicons/vue/24/outline'

const auth = useAuthStore()
</script>
