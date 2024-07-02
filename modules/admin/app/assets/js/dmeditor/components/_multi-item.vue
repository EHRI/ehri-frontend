<script lang="ts">

export default {
  props: {
    label: String,
    modelValue: Array,
    text: Boolean,
    type: {
      type: String,
      default: "text",
    }
  },
  data: function () {
    return {
      state: this.modelValue ? this.modelValue : [],
      pending: null,
    };
  },
  methods: {
    update: function() {
      this.$emit("update:modelValue", this.state);
    },
    addPending: function () {
      if (this.pending) {
        this.state.push(this.pending);
        this.pending = null;
      }
    },
  },
  watch: {
    modelValue: function (newData) {
      this.state = newData;
    },
  },
}
</script>

<template>
    <div class="form-group">
        <label>{{ label }}</label>
        <div class="input-group" v-for="(item, i) in state">
            <textarea rows="1" v-if="text" class="form-control" v-model.trim="state[i]" v-on:change="update"></textarea>
            <input v-bind:type="type" v-else class="form-control" v-model.trim="state[i]" v-on:change="update"/>
            <span class="input-group-addon" v-on:click="state.splice(i, 1)">
                    <button class="btn"><i class="fa fa-remove"></i></button>
               </span>
        </div>
        <div class="input-group">
           <textarea rows="1" v-if="text" class="form-control" placeholder="Add New..."
                     v-on:change="addPending" v-model.trim="pending"></textarea>
            <input v-else v-bind:type="type" class="form-control" placeholder="Add New..." v-on:change="addPending"
                   v-model.trim="pending"/>
            <span class="input-group-btn">
             <button class="btn" v-bind:disabled="pending === ''"
                     v-bind:class="{'btn-success': pending}" v-on:click="addPending">
              <i class="fa fa-plus-circle"></i>
             </button>
           </span>
        </div>
    </div>
</template>
