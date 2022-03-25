<script lang="ts">

import Vue from 'vue';
import {ITerminalAddon, Terminal} from 'xterm';
import {FitAddon} from 'xterm-addon-fit';
import {WebLinksAddon} from 'xterm-addon-web-links';

import 'xterm/css/xterm.css';


export default {
  props: {
    log: Terminal,
    panelSize: Number,
    visible: Boolean,
    resize: Number, // not a usable value, just a watch trigger
  },
  methods: {
    fit () {
      let parent = this.$el.parentNode;
      Vue.nextTick(() => {
        let width = parent.offsetWidth - this.$el.offsetLeft + parent.offsetLeft;
        let height = parent.offsetHeight - this.$el.offsetTop + parent.offsetTop;
        if (width && height) {
          this.$el.style.width = width + "px"
          this.$el.style.height = height + "px"
          this.$fitAddon.fit();
        }
      });
    },
  },
  watch: {
    panelSize: function() { this.fit(); },
    visible: function() { this.fit(); },
    resize: function() { this.fit(); },
  },
  mounted () {
    this.$fitAddon = new FitAddon() as ITerminalAddon;
    this.log.loadAddon(this.$fitAddon);

    const webLinksAddon = new WebLinksAddon() as ITerminalAddon;
    this.log.loadAddon(webLinksAddon);

    this.log.open(this.$el);

    window.addEventListener("resize", this.fit);
    this.fit();
  },
};
</script>

<template>
  <div class="xterm"></div>
</template>
