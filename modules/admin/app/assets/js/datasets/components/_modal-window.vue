<script lang="ts">



export default {
  props: {
    resizable: {
      type: Boolean,
      default: false,
    }
  },
  data: function() {
    return {
      minWidth: 800,
      minHeight: 300,
    }
  },
  methods: {
    move: function(evt: MouseEvent) {
      evt.preventDefault();
      let parent = this.$el.querySelector(".modal-dialog-centered"),
          content = this.$el.querySelector(".modal-content"),
          width = window.visualViewport.width,
          height = window.visualViewport.height,
          posX = evt.clientX,
          posY = evt.clientY;

      parent.style.maxWidth = Math.max(this.minWidth, ((posX - (width / 2)) * 2)) + "px";
      content.style.height = Math.max(this.minHeight, (posY - (height / 2)) * 2) + "px";
      this.$emit("move");
    },

    resize: function(evt: MouseEvent) {
      evt.preventDefault();

      window.addEventListener("mousemove", this.move);

      window.addEventListener("mouseup", (evt: MouseEvent) => {
        if (evt.button === 0) {
          console.debug("Stop resize");
          this.$emit("resize");
          window.removeEventListener("mousemove", this.move);
        }
      }, {once: true});
    }
  },
  mounted() {
    let parent = this.$el.querySelector(".modal-dialog-centered"),
        content = this.$el.querySelector(".modal-content");
    this.minWidth = parent.clientWidth;
    this.minHeight = content.clientHeight;
  }
};
</script>

<template>
  <div class="modal show fade" tabindex="-1" role="dialog" style="display: block">
    <div class="modal-dialog modal-dialog-centered" role="document">
      <div v-bind:class="{resizable: resizable}" class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            <slot name="title"></slot>
          </h5>
          <button type="button" class="close" aria-label="Close" v-on:click="$emit('close')">
            <span aria-hidden="true">&times;</span>
          </button>
        </div>
        <div class="modal-body">
          <slot></slot>
        </div>
        <div class="modal-footer">
          <slot name="footer"></slot>
        </div>
        <div v-if="resizable" class="modal-resize-handle" v-on:mousedown.left="resize">
        </div>
      </div>
    </div>
  </div>
</template>

