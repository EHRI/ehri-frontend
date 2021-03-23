<script lang="ts">

export default {
  props: {
    label: String,
    data: Array,
    text: Boolean
  },
  data: function () {
    return {
      state: this.data,
      pending: null,
    };
  },
  methods: {
    addPending: function () {
      if (this.pending) {
        this.state.push(this.pending);
        this.pending = null;
      }
    },
  },
  watch: {
    data: function (newData) {
      this.state = newData;
    },
  },
}
</script>

<template>
  <div class="concept-description-item">
    <label class="label">{{label}}</label>
    <div class="controls">
      <div class="input-group" v-for="(item, i) in state">
        <textarea rows="1" v-if="text" class="form-control" v-model.trim="state[i]"></textarea>
        <input v-else class="form-control" v-model.trim="state[i]"/>
        <span class="input-group-addon" v-on:click="state.splice(i, 1)">
            <i class="fa fa-remove"></i>
           </span>
      </div>
      <div class="input-group">
           <textarea rows="1" v-if="text" class="form-control" placeholder="Add New..."
                     v-on:change="addPending" v-model.trim="pending"></textarea>
        <input v-else class="form-control" placeholder="Add New..." v-on:change="addPending" v-model.trim="pending"/>
        <span class="input-group-btn">
             <button class="btn" v-bind:disabled="pending === ''"
                     v-bind:class="{'btn-success': pending}" v-on:click="addPending">
              <i class="fa fa-plus-circle"></i>
             </button>
           </span>
      </div>
    </div>
  </div>
</template>
