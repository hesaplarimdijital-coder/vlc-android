import { Tooltip } from 'bootstrap'

export const tooltip = {

  mounted(el) {
    let tooltip = new Tooltip(el)
    el.tt = tooltip
  },

  unmounted(el) {
   el.tt.hide()
  }

}