<script lang="ts">

// NB: This mixin assumes MixinTasklog is also present.

import _forEach from 'lodash/forEach';
import _isUndefined from 'lodash/isUndefined';
import _isEmpty from 'lodash/isEmpty';
import _keys from 'lodash/keys';
import _find from 'lodash/find';
import {ValidationResult} from "../types";
import {red, green} from "../termcolors";

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
    }
  },
  methods: {
    handleValidationResults: function (errs: ValidationResult[]) {
      _forEach(errs, res => {
        this.validationResults[res.eTag] = res.errors;
        delete this.validating[res.eTag];
      });
      if (_isUndefined(_find(errs, (err) => err.errors.length > 0))) {
        this.println(green("No errors found 😀"));
      } else {
        errs.forEach(res => {
          if (res.errors.length > 0) {
            this.println(red(decodeURI(res.key)));
            res.errors.forEach(err => {
              this.println("    " + err.line + "/" + err.pos + " - " + err.error);
            })
          }
        });
      }
    },
    validateFiles: function (tagToKey: object) {
      this.tab = 'info';
      this.validationRunning = true;
      // this.log.clear();
      let allTags = _isEmpty(tagToKey) ? this.files.map(f => f.eTag) : _keys(tagToKey);
      _forEach(allTags, tag => this.validating[tag] = true);;

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
