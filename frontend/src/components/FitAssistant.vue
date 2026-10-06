<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { api, ApiError, type FitAnalysis, type ProductFitGuidance, type Variant } from '../api'
import { errorCopy } from '../format'
import { messageLabel, t } from '../i18n'

const props = defineProps<{ productId: string; fitSupported?: boolean; fitGuidance?: ProductFitGuidance | null; selectedColor?: string; variants: Variant[] }>()
const emit = defineEmits<{ 'select-size': [size: string]; 'select-color': [color: string, size?: string] }>()

type Step = 'closed' | 'choose' | 'quick' | 'quick-result' | 'photo' | 'check' | 'analyzing' | 'result'
type Perception = 'TIGHT' | 'ABOUT_RIGHT' | 'LOOSE'
const perceptionOptions: Perception[] = ['TIGHT', 'ABOUT_RIGHT', 'LOOSE']
const step = ref<Step>('closed')
const usualSize = ref('')
const perception = ref<Perception>()
const file = ref<File>()
const preview = ref('')
const result = ref<FitAnalysis>()
const error = ref('')
const entryButton = ref<HTMLButtonElement>()
const panelHeading = ref<HTMLElement>()
const stepHeading = ref<HTMLElement>()

const productSizes = computed(() => [...new Set(props.variants.map(variant => variant.size))]
  .sort((left, right) => left.localeCompare(right, undefined, { numeric: true })))
const quickAvailableColors = computed(() => [...new Set(props.variants
  .filter(variant => variant.size === usualSize.value && variant.availability === 'AVAILABLE')
  .map(variant => variant.color))])
const quickProductAvailable = computed(() => quickAvailableColors.value.length > 0)
const quickSelectedColorAvailable = computed(() => !props.selectedColor || props.variants.some(variant => variant.size === usualSize.value
  && variant.color === props.selectedColor && variant.availability === 'AVAILABLE'))
const quickColorActions = computed(() => props.selectedColor && quickSelectedColorAvailable.value ? [] : quickAvailableColors.value)
const selectedColorAvailable = computed(() => {
  if (!result.value || !props.selectedColor) return result.value?.selectedColorAvailable !== false
  return props.variants.some(variant => variant.size === result.value?.recommendedSize
    && variant.color === props.selectedColor && variant.availability === 'AVAILABLE')
})
const showStockWarning = computed(() => result.value?.recommendedAvailable === false || !selectedColorAvailable.value)

function revokePreview() { if (preview.value) URL.revokeObjectURL(preview.value); preview.value = '' }
function clearImage() { revokePreview(); file.value = undefined; result.value = undefined; error.value = '' }
function clearQuick() { usualSize.value = ''; perception.value = undefined }
async function focus(target: typeof panelHeading | typeof stepHeading | typeof entryButton) { await nextTick(); target.value?.focus() }
function open() { clearImage(); clearQuick(); step.value = 'choose'; void focus(panelHeading) }
function close() { clearImage(); clearQuick(); step.value = 'closed'; void focus(entryButton) }
function startQuick() { clearImage(); step.value = 'quick'; void focus(stepHeading) }
function chooseUsualSize(event: Event) { usualSize.value = (event.target as HTMLSelectElement).value }
function showQuickResult() { if (!usualSize.value || !perception.value) return; step.value = 'quick-result'; void focus(stepHeading) }
function startPhoto() { clearImage(); step.value = 'photo'; void focus(stepHeading) }
function resetPhoto() { clearImage(); step.value = 'photo'; void focus(stepHeading) }
function pick(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  acceptImage(selected)
  ;(event.target as HTMLInputElement).value = ''
}
function drop(event: DragEvent) {
  if (event.dataTransfer?.files.length !== 1) { error.value = 'Choose one photo at a time.'; return }
  acceptImage(event.dataTransfer.files[0])
}
function acceptImage(selected?: File) {
  if (!selected) return
  clearImage()
  if (!['image/jpeg', 'image/png'].includes(selected.type) || !selected.size || selected.size > 5 * 1024 * 1024) {
    error.value = 'Only PNG or JPEG images up to 5 MB are accepted.'; step.value = 'photo'; return
  }
  revokePreview(); file.value = selected; preview.value = URL.createObjectURL(selected); error.value = ''; step.value = 'check'; void focus(stepHeading)
}
async function analyze() {
  if (!file.value) return
  step.value = 'analyzing'; error.value = ''; void focus(stepHeading)
  try { result.value = await api.fitAnalysis(props.productId, file.value, props.selectedColor); step.value = 'result' }
  catch (reason) { error.value = reason instanceof ApiError ? messageLabel(errorCopy(reason)) : t('The image could not be used.'); step.value = 'result' }
  void focus(stepHeading)
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
function fitTendencyLabel() {
  const value = props.fitGuidance?.fitTendency
  return t(value === 'RUNS_SMALL' ? 'Runs small' : value === 'RUNS_LARGE' ? 'Runs large' : 'True to size')
}
function widthProfileLabel() {
  const value = props.fitGuidance?.widthProfile
  return t(value === 'NARROW' ? 'Narrow' : value === 'WIDE' ? 'Wide' : 'Regular')
}
function perceptionLabel() {
  return t(perception.value === 'TIGHT' ? 'tight' : perception.value === 'LOOSE' ? 'loose' : 'about right')
}
function selectSize(size?: string) { if (size) emit('select-size', size) }
function selectColor(color: string, size = result.value?.recommendedSize ?? usualSize.value) { emit('select-color', color, size) }
function confidenceLabel(confidence?: string) { return t(confidence === 'HIGH' ? 'Strong photo quality' : 'Usable image estimate') }

onBeforeUnmount(revokePreview)
</script>

<template>
  <p v-if="fitSupported === false" class="fit-unsupported" role="status">{{ t('This shoe model does not have a supported fit profile yet.') }}</p>
  <section v-else class="fit-assistant" :class="{ 'is-closed': step === 'closed' }" :aria-labelledby="step === 'closed' ? 'fit-heading' : 'fit-panel-heading'">
    <div class="fit-assistant-heading">
      <div>
        <h2 v-if="step === 'closed'" id="fit-heading">{{ t('Not sure about your size?') }}</h2>
        <h2 v-else id="fit-panel-heading" ref="panelHeading" tabindex="-1">{{ t('Find my size') }}</h2>
        <p>{{ t(step === 'closed' ? 'Quick size or optional photo guidance' : 'Choose quick guidance or an A4-calibrated photo estimate.') }}</p>
      </div>
      <button v-if="step === 'closed'" ref="entryButton" class="fit-entry" type="button" @click="open"><span>{{ t('Find my size') }}</span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg></button>
      <button v-else class="text-button" type="button" @click="close">{{ t('Close') }}</button>
    </div>

    <template v-if="step === 'choose'">
      <div class="fit-step-copy"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Choose a guidance method') }}</h3><p>{{ t('Both paths keep the final size choice with you.') }}</p></div>
      <div class="fit-methods">
        <button type="button" @click="startQuick"><strong>{{ t('I know my usual size') }}</strong><span>{{ t('Use an EU size you already wear. It will not be adjusted automatically.') }}</span></button>
        <button type="button" @click="startPhoto"><strong>{{ t('Measure with a photo') }}</strong><span>{{ t('Use a calibrated image estimate. A fully visible A4 sheet is required.') }}</span></button>
      </div>
    </template>

    <template v-else-if="step === 'quick'">
      <div class="fit-step-copy"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Use my usual size') }}</h3><p>{{ t('Size options come from this product only.') }}</p></div>
      <div class="fit-quick-form">
        <label for="fit-usual-size">{{ t('What EU size do you usually wear?') }}</label>
        <select id="fit-usual-size" :value="usualSize" @change="chooseUsualSize"><option value="" disabled>{{ t('Choose an EU size') }}</option><option v-for="size in productSizes" :key="size" :value="size">EU {{ size }}</option></select>
        <fieldset><legend>{{ t('How does your current footwear feel?') }}</legend><label v-for="option in perceptionOptions" :key="option"><input type="radio" name="fit-perception" :value="option" :checked="perception === option" @change="perception = option" /><span>{{ t(option === 'TIGHT' ? 'Tight' : option === 'LOOSE' ? 'Loose' : 'About right') }}</span></label></fieldset>
        <div class="fit-actions"><button class="primary-button" type="button" :disabled="!usualSize || !perception" @click="showQuickResult">{{ t('Show my starting point') }}</button><button class="text-button" type="button" @click="startPhoto">{{ t('Measure with a photo') }}</button></div>
      </div>
    </template>

    <template v-else-if="step === 'quick-result'">
      <div class="fit-result fit-quick-result" aria-live="polite">
        <h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Start with EU {size}', { size: usualSize }) }}</h3>
        <p>{{ t('Your usual EU {size} feels {feeling}.', { size: usualSize, feeling: perceptionLabel() }) }}</p>
        <dl v-if="fitGuidance" class="fit-evidence"><div><dt>{{ t('Fit tendency') }}</dt><dd>{{ fitTendencyLabel() }}</dd></div><div><dt>{{ t('Width profile') }}</dt><dd>{{ widthProfileLabel() }}</dd></div></dl>
        <p class="fit-disclaimer">{{ t('This is a starting point, not an automatic size adjustment.') }}</p>
        <div v-if="!quickProductAvailable" class="fit-stock-warning"><strong>{{ t('This size is currently unavailable.') }}</strong></div>
        <div v-else-if="!quickSelectedColorAvailable" class="fit-stock-warning"><strong>{{ t('This size is unavailable in the selected color.') }}</strong><span>{{ t('Available in another color:') }} {{ quickAvailableColors.map(color => t(color)).join(', ') }}</span></div>
        <div class="fit-actions"><button v-if="quickProductAvailable" class="primary-button" type="button" @click="selectSize(usualSize)">{{ t('Apply EU {size}', { size: usualSize }) }}</button><button class="text-button" type="button" @click="startQuick">{{ t('Change my answers') }}</button></div>
        <div v-if="quickColorActions.length" class="fit-color-actions"><button v-for="color in quickColorActions" :key="color" type="button" class="text-button" @click="selectColor(color, usualSize)">{{ t('Choose {color}', { color: t(color) }) }}</button></div>
      </div>
    </template>

    <template v-else-if="step === 'photo'">
      <div class="fit-step-copy"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Photo') }}</h3><p>{{ t('Show the entire A4 sheet and one whole foot inside it.') }}</p><p>{{ t('Keep the camera close to overhead, use good light, and avoid heavy shadows.') }}</p></div>
      <div class="fit-sequence" :aria-label="t('Photo, review, result')"><span class="active">{{ t('Photo') }}</span><span>{{ t('Review') }}</span><span>{{ t('Result') }}</span></div>
      <div class="fit-drop-zone" @dragover.prevent @drop.prevent="drop">
        <p>{{ t('Drop one photo here, or choose a file.') }}</p>
        <input id="fit-photo-input" class="fit-upload-input" type="file" accept="image/png,image/jpeg" capture="environment" aria-describedby="fit-upload-help" @change="pick" />
        <label class="fit-upload-label" for="fit-photo-input">{{ t('Choose or take a photo') }}</label>
        <p id="fit-upload-help">{{ t('Only PNG or JPEG images up to 5 MB are accepted.') }}</p>
        <p>{{ t('Show heel, toes and all A4 corners. Shoot from above in good light; avoid tilted views. Without a usable reference, no size can be estimated.') }}</p>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ t(error) }}</p>
      <button class="text-button fit-method-switch" type="button" @click="startQuick">{{ t('Use my usual size instead') }}</button>
    </template>

    <template v-else-if="step === 'check'">
      <div class="fit-step-copy"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Review your photo') }}</h3></div>
      <div class="fit-preview"><img :src="preview" :alt="t('Photo preview')" /></div>
      <p class="fit-check-copy">{{ t('Check that the A4 corners and the whole foot are visible before analyzing.') }}</p>
      <div class="fit-actions"><button class="primary-button" type="button" @click="analyze">{{ t('Use this photo') }}</button><button class="text-button" type="button" @click="resetPhoto">{{ t('Choose another photo') }}</button></div>
    </template>

    <template v-else-if="step === 'analyzing'">
      <div class="fit-busy" role="status" aria-live="polite"><span class="loader-mark"></span><div><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Analyzing measurement…') }}</h3><p>{{ t('The image is used only for this request and is not saved.') }}</p></div></div>
    </template>

    <template v-else-if="step === 'result'">
      <div v-if="error || result?.status === 'RETAKE'" class="fit-result fit-retry" role="alert"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Try a clearer photo') }}</h3><p>{{ error || reasonLabel(result?.retakeReason) }}</p><div class="fit-actions"><button class="primary-button" type="button" @click="resetPhoto">{{ t('Retake photo') }}</button><button class="text-button" type="button" @click="startQuick">{{ t('Use my usual size instead') }}</button></div></div>
      <div v-else-if="result?.status === 'UNSUPPORTED_PRODUCT'" class="fit-result" role="status"><h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('This shoe model does not have a supported fit profile yet.') }}</h3></div>
      <div v-else-if="result" class="fit-result" aria-live="polite">
        <h3 id="fit-step-heading" ref="stepHeading" tabindex="-1">{{ t('Your suggested size') }} <strong>EU {{ result.recommendedSize }}</strong></h3>
        <p class="fit-confidence"><strong>{{ confidenceLabel(result.analysisConfidence) }}</strong><span>{{ t('This describes image quality, not the probability that the shoe will fit.') }}</span></p>
        <p>{{ explanationLabel(result.explanation) }}</p><p v-if="result.warning" class="fit-warning">{{ warningLabel(result.warning) }}</p>
        <div class="fit-actions"><button class="primary-button" type="button" @click="selectSize(result.recommendedSize)">{{ t('Select EU {size}', { size: result.recommendedSize ?? '' }) }}</button><button v-if="result.alternativeSize" class="text-button fit-alternative" type="button" @click="selectSize(result.alternativeSize)">{{ t('Alternative size: EU {size}', { size: result.alternativeSize }) }}</button></div>
        <div v-if="showStockWarning" class="fit-stock-warning"><strong v-if="result.recommendedAvailable === false">{{ t('Recommended size is currently unavailable.') }}</strong><strong v-else>{{ t('Recommended size is unavailable in the selected color.') }}</strong><span v-if="result.recommendedAvailable !== false && !selectedColorAvailable">{{ t('Available in another color:') }} {{ result.availableColors.map(color => t(color)).join(', ') }}</span><span v-if="result.recommendedAvailable === false && !selectedColorAvailable">{{ t('Recommended size is unavailable in the selected color.') }}</span><div v-if="result.availableColors.length" class="fit-color-actions"><button v-for="color in result.availableColors" :key="color" type="button" class="text-button" @click="selectColor(color)">{{ t('Choose {color}', { color: t(color) }) }}</button></div></div>
        <p class="fit-disclaimer">{{ t('Photo analysis is advisory, not a guarantee. Analysis confidence reflects photo quality, not fit probability. You can always choose another size.') }}</p>
      </div>
    </template>
  </section>
</template>
