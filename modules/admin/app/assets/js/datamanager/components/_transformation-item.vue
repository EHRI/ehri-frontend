<script lang="ts">

export default {
  props: {
    item: Object,
    parameters: {
      type: Object,
      default: null
    },
    deleteable: Boolean,
  },
}
</script>

<template functional>
  <div v-on:dblclick="listeners.edit()"
       class="list-group-item transformation-item list-group-item-action">
    <h4 class="transformation-item-name">
      {{ props.item.name }}
      <span class="transformation-item-comments" v-bind:title="props.item.comments">{{ props.item.comments }}</span>
    </h4>
    <div class="transformation-item-params">
      <button v-if="props.item.hasParams && props.parameters" v-bind:class="{
          'btn-dark': Object.keys(props.parameters).length > 0,
          'btn-default': Object.keys(props.parameters).length === 0
        }" class="btn btn-sm" v-on:click="listeners['edit-params']()">
        <i class="fa fa-fw fa-gears"></i>
      </button>
    </div>
    <button v-if="props.deleteable" class="transformation-item-edit btn btn-sm btn-outline-danger" v-on:click="listeners['delete']()">
      <i class="fa fa-fw fa-trash-o"></i>
    </button>
    <button v-else class="transformation-item-edit btn btn-sm btn-default" v-on:click="listeners.edit()">
      <i class="fa fa-fw fa-edit"></i>
    </button>

    <span class="transformation-item-meta">
      <span class="badge badge-pill" v-bind:class="'badge-' + props.item.bodyType">{{ props.item.bodyType }}</span>
      <span v-if="!props.item.repoId" class="badge badge-light">Generic</span>
    </span>
  </div>
</template>
