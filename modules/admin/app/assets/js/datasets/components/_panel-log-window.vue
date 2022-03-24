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

  watch: {
    panelSize: function() {
      this.fit();
    },

    visible: function(value: boolean) {
      if (value) {
        this.fit();
      }
    },
    resize: function() {
      this.fit();
    }
  },
  mounted () {
    // let term = new Terminal(this.options);
    this.$fitAddon = new FitAddon() as ITerminalAddon;
    this.log.loadAddon(this.$fitAddon);

    const webLinksAddon = new WebLinksAddon() as ITerminalAddon;
    this.log.loadAddon(webLinksAddon);

    this.log.open(this.$el);

    window.addEventListener("resize", this.fit);

    if (this.visible) {
      this.fit();
    }
  },
  methods: {
    fit () {
      let parent = this.$el.parentNode;
      Vue.nextTick(() => {
        this.$el.style.width = (parent.offsetWidth - this.$el.offsetLeft + parent.offsetLeft) + "px"
        this.$el.style.height = (parent.offsetHeight - this.$el.offsetTop + parent.offsetTop) + "px"
        this.$fitAddon.fit();
      });
    },
  }
};
</script>

<template>
  <div class="xterm"></div>
</template>
