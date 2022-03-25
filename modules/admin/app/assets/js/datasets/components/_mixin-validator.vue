<script lang="ts">

import _forEach from 'lodash-es/forEach';
import _isUndefined from 'lodash-es/isUndefined';
import _isEmpty from 'lodash-es/isEmpty';
import _keys from 'lodash-es/keys';
import _find from 'lodash-es/find';
import {ValidationResult} from "../types";

export default {
  props: {
    datasetId: String,
    fileStage: String,
    api: Object,
  },
  data: function () {
    return {
      validating: {},
      validationRunning: false,
      validationResults: {},
      validationLog: [],
    }
  },
  methods: {
    handleValidationResults: function (errs: ValidationResult[]) {
      _forEach(errs, res => {
        this.$set(this.validationResults, res.eTag, res.errors)
        this.$delete(this.validating, res.eTag);
      });
      if (_isUndefined(_find(errs, (err) => err.errors.length > 0))) {
        this.validationLog.push('<span class="text-success">No errors found ✓</span>');
      } else {
        errs.forEach(res => {
          if (res.errors.length > 0) {
            this.validationLog.push('<span class="text-danger">' + decodeURI(res.key) + ':</span>')
            res.errors.forEach(err => {
              this.validationLog.push("    " + err.line + "/" + err.pos + " - " + err.error);
            })
          }
        });
      }
    },
    validateFiles: function (tagToKey: object) {
      this.tab = 'validation';
      this.validationRunning = true;
      this.validationLog = [];
      let allTags = _isEmpty(tagToKey) ? this.files.map(f => f.eTag) : _keys(tagToKey);
      _forEach(allTags, tag => this.$set(this.validating, tag, true));

      this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
        .then(errs => this.handleValidationResults(errs))
        .catch(error => this.showError("Error attempting validation", error))
        .finally(() => {
          this.validating = {};
          this.validationRunning = false;
        });
    },
  }
}
</script>
