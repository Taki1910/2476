<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { api, ApiError, type FitAnalysis, type Variant } from '../api'
import { errorCopy } from '../format'
import { messageLabel, t } from '../i18n'

const props = defineProps<{ productId: string; fitSupported?: boolean; selectedColor?: string; variants: Variant[] }>()
const emit = defineEmits<{ 'select-size': [size: string]; 'select-color': [color: string, size?: string] }>()

type Step = 'closed' | 'prepare' | 'photo' | 'check' | 'analyzing' | 'result'
const step = ref<Step>('closed')
const file = ref<File>()
const preview = ref('')
const result = ref<FitAnalysis>()
const error = ref('')

const selectedColorAvailable = computed(() => {
  if (!result.value || !props.selectedColor) return result.value?.selectedColorAvailable !== false
  return props.variants.some(variant => variant.size === result.value?.recommendedSize
    && variant.color === props.selectedColor && variant.availability === 'AVAILABLE')
})
const showStockWarning = computed(() => result.value?.recommendedAvailable === false || !selectedColorAvailable.value)

function revokePreview() { if (preview.value) URL.revokeObjectURL(preview.value); preview.value = '' }
function clearImage() { revokePreview(); file.value = undefined; result.value = undefined; error.value = '' }
function open() { clearImage(); step.value = 'prepare' }
function close() { clearImage(); step.value = 'closed' }
function resetPhoto() { clearImage(); step.value = 'photo' }
function pick(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  if (!selected) return
  if (!['image/jpeg', 'image/png'].includes(selected.type) || selected.size > 5 * 1024 * 1024) {
    error.value = 'Only PNG or JPEG images up to 5 MB are accepted.'; step.value = 'photo'; return
  }
  revokePreview(); file.value = selected; preview.value = URL.createObjectURL(selected); error.value = ''; step.value = 'check'
}
async function analyze() {
  if (!file.value) return
  step.value = 'analyzing'; error.value = ''
  try { result.value = await api.fitAnalysis(props.productId, file.value, props.selectedColor); step.value = 'result' }
  catch (reason) { error.value = reason instanceof ApiError ? messageLabel(errorCopy(reason)) : t('The image could not be used.'); step.value = 'result' }
}
function reasonLabel(reason?: string) {
  return t(({ REFERENCE_NOT_FOUND: 'Reference sheet not found', REFERENCE_CLIPPED: 'Reference sheet is clipped', EXCESSIVE_PERSPECTIVE: 'The camera angle is too distorted', IMAGE_TOO_BLURRY: 'The image is too blurry', FOOT_NOT_FOUND: 'A whole foot was not found', FOOT_PARTIAL: 'The foot is too close to the sheet edge', IMPLAUSIBLE_MEASUREMENT: 'The measured geometry is not plausible', ANALYSIS_INSUFFICIENT: 'The image quality is not sufficient', FIT_PROFILE_OUT_OF_RANGE: 'Measurement outside this model profile' } as Record<string, string>)[reason ?? ''] ?? 'The image could not be used.')
}
function explanationLabel(explanation?: string) {
  return t(({ FIT_TENDENCY_SMALL: 'This model runs small. Its product-specific ranges already account for that.', FIT_TENDENCY_LARGE: 'This model runs large. Its product-specific ranges already account for that.', FIT_TENDENCY_TRUE: 'This model uses its product-specific length and width ranges.' } as Record<string, string>)[explanation ?? ''] ?? '')
}
function warningLabel(warning?: string) {
  return t(warning === 'WIDTH_SIZE_UP' ? 'Width moved the recommendation up one size.' : 'The measured width may not match this model well.')
}
function selectSize(size?: string) { if (size) emit('select-size', size) }
function selectColor(color: string) { emit('select-color', color, result.value?.recommendedSize) }
function confidenceLabel(confidence?: string) { return t(confidence === 'HIGH' ? 'High' : 'Medium') }

onBeforeUnmount(revokePreview)
</script>

<template>
  <section class="fit-assistant" aria-labelledby="fit-heading">
    <div class="fit-assistant-heading">
      <div><h2 id="fit-heading">{{ t('Find my size') }}</h2><p>{{ t('Photo-assisted fit recommendation') }}</p></div>
      <button v-if="step !== 'closed' && fitSupported !== false" class="text-button" type="button" @click="close">{{ t('Close') }}</button>
    </div>

    <template v-if="fitSupported === false">
      <p class="fit-unsupported">{{ t('This shoe model does not have a supported fit profile yet.') }}</p>
    </template>
    <template v-else-if="step === 'closed'">
      <button class="fit-entry" type="button" @click="open"><span>{{ t('Find my size') }}</span><small>{{ t('Photo-assisted fit recommendation') }}</small><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg></button>
    </template>
    <template v-else-if="step === 'prepare'">
      <div class="fit-step-copy"><h3>{{ t('Prepare your photo') }}</h3><p>{{ t('Use A4 paper, place it flat, and show the whole sheet and foot.') }}</p><p>{{ t('Keep the camera close to overhead, use good light, and avoid heavy shadows.') }}</p></div>
      <div class="fit-sequence" aria-label="Prepare, photo, check, analyze, recommendation"><span class="active">{{ t('Prepare') }}</span><span>{{ t('Photo') }}</span><span>{{ t('Check') }}</span><span>{{ t('Analyze') }}</span><span>{{ t('Recommendation') }}</span></div>
      <button class="primary-button" type="button" @click="step = 'photo'">{{ t('Start with a photo') }}</button>
    </template>
    <template v-else-if="step === 'photo'">
      <div class="fit-step-copy"><h3>{{ t('Choose or take a photo') }}</h3><p>{{ t('Show the entire A4 sheet and one whole foot inside it.') }}</p></div>
      <input id="fit-photo-input" class="fit-upload-input" type="file" accept="image/png,image/jpeg" capture="environment" @change="pick" />
      <label class="fit-upload-label" for="fit-photo-input">{{ t('Choose or take a photo') }}</label>
      <p v-if="error" class="form-error" role="alert">{{ t(error) }}</p>
    </template>
    <template v-else-if="step === 'check'">
      <div class="fit-preview"><img :src="preview" :alt="t('Photo preview')" /></div>
      <p class="fit-check-copy">{{ t('Check that the A4 corners and the whole foot are visible before analyzing.') }}</p>
      <div class="fit-actions"><button class="primary-button" type="button" @click="analyze">{{ t('Use this photo') }}</button><button class="text-button" type="button" @click="resetPhoto">{{ t('Choose another photo') }}</button></div>
    </template>
    <template v-else-if="step === 'analyzing'">
      <div class="fit-busy" role="status" aria-live="polite"><span class="loader-mark"></span><h3>{{ t('Analyzing measurement…') }}</h3><p>{{ t('The image is used only for this request and is not saved.') }}</p></div>
    </template>
    <template v-else-if="step === 'result'">
      <div v-if="error || result?.status === 'RETAKE'" class="fit-result fit-retry" role="alert"><h3>{{ t('Try a clearer photo') }}</h3><p>{{ error || reasonLabel(result?.retakeReason) }}</p><button class="primary-button" type="button" @click="resetPhoto">{{ t('Retake photo') }}</button></div>
      <div v-else-if="result?.status === 'UNSUPPORTED_PRODUCT'" class="fit-result" role="status"><h3>{{ t('This shoe model does not have a supported fit profile yet.') }}</h3></div>
      <div v-else-if="result" class="fit-result" aria-live="polite">
        <h3>{{ t('Your suggested size') }} <strong>EU {{ result.recommendedSize }}</strong></h3>
        <dl class="fit-measurements"><div><dt>{{ t('Length') }}</dt><dd>{{ result.footLengthMm?.toFixed(1) }} mm</dd></div><div><dt>{{ t('Width') }}</dt><dd>{{ result.footWidthMm?.toFixed(1) }} mm</dd></div><div><dt>{{ t('Analysis confidence') }}</dt><dd>{{ confidenceLabel(result.analysisConfidence) }}</dd></div></dl>
        <p>{{ explanationLabel(result.explanation) }}</p><p v-if="result.warning" class="fit-warning">{{ warningLabel(result.warning) }}</p>
        <button class="primary-button" type="button" @click="selectSize(result.recommendedSize)">{{ t('Select EU {size}', { size: result.recommendedSize ?? '' }) }}</button>
        <button v-if="result.alternativeSize" class="text-button fit-alternative" type="button" @click="selectSize(result.alternativeSize)">{{ t('Alternative size: EU {size}', { size: result.alternativeSize }) }}</button>
        <div v-if="showStockWarning" class="fit-stock-warning"><strong v-if="result.recommendedAvailable === false">{{ t('Recommended size is currently unavailable.') }}</strong><strong v-else>{{ t('Recommended size is unavailable in the selected color.') }}</strong><span v-if="result.recommendedAvailable !== false && !selectedColorAvailable">{{ t('Available in another color:') }} {{ result.availableColors.map(color => t(color)).join(', ') }}</span><span v-if="result.recommendedAvailable === false && !selectedColorAvailable">{{ t('Recommended size is unavailable in the selected color.') }}</span><div v-if="result.availableColors.length" class="fit-color-actions"><button v-for="color in result.availableColors" :key="color" type="button" class="text-button" @click="selectColor(color)">{{ t('Choose {color}', { color: t(color) }) }}</button></div></div>
        <p class="fit-disclaimer">{{ t('Photo analysis is advisory, not a guarantee. Analysis confidence reflects photo quality, not fit probability. You can always choose another size.') }}</p>
      </div>
    </template>
  </section>
</template>
