<script>

import _isObject from 'lodash/isObject';

export default {
  props: {
    modelValue: Object,
  },
  data: function() {
    return {
      show: _isObject(this.modelValue),
      username: this.modelValue ? this.modelValue.username : "",
      password: this.modelValue ? this.modelValue.password : "",

    }
  },
  methods: {
    update: function() {
      this.$emit("update:modelValue", this.auth)
    }
  },
  computed: {
    auth: function() {
      return this.show ? {
        username: this.username,
        password: this.password
      } : null;
    },
  },
  watch: {
    show: function() {
      this.update();
    }
  }
}
</script>

<template>
  <div class="http-basic-auth-params">
    <div class="form-group">
      <div class="form-check">
        <input type="checkbox" class="form-check-input" id="opt-auth" v-model="show"/>
        <label class="form-check-label" for="opt-auth">
          HTTP Basic Authentication
        </label>
      </div>
    </div>
    <fieldset v-if="show">
      <div class="form-group">
        <label class="form-label" for="opt-auth-username">
          Username
        </label>
        <input v-model="username" v-on:change="update" class="form-control" id="opt-auth-username" type="text" autocomplete="off"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-auth-password">
          Password
        </label>
        <input v-model="password" v-on:change="update" class="form-control" id="opt-auth-password" type="password" autocomplete="off"/>
      </div>
    </fieldset>
  </div>
</template>
