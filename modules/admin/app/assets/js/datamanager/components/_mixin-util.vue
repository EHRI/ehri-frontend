<script lang="ts">

import _extend from 'lodash/extend';
import _isEmpty from 'lodash/isEmpty';
import _has from 'lodash/has';
import _fromPairs from 'lodash/fromPairs';
import _toPairs from 'lodash/toPairs';
import _omit from 'lodash/omit';
import _set from 'lodash/set';

import {prettyDate, humanFileSize} from "../common";

export default {
  methods: {
    decodeURI,
    prettyDate,
    humanFileSize,

    removeQueryParam: function(qs: string, names: string[]): string {
      let qp = this.queryParams(qs);
      return this.queryString(_omit(qp, names));
    },
    setQueryParam: function(qs: string, name: string, value: string): string {
      let qp = this.queryParams(qs);
      return this.queryString(_set(qp, name, value));
    },
    getQueryParam: function(qs: object, name: string): string | null {
      let qp = this.queryParams(qs);
      return _has(qp, name) ? qp[name] : null;
    },
    queryParams: function(qs: string): object {
      let qsp = (qs && qs[0] === '?') ? qs.slice(1): qs;
      return (qsp && qsp.trim() !== "")
        ? _fromPairs(qsp.split("&").map(p => p.split("=")))
        : {};
    },
    queryString: function(qp: object): string {
      return !_isEmpty(qp)
        ? ("?" + _toPairs(qp).map(p => p.join("=")).join("&"))
        : "";
    },
    removeUrlState: function(key: string): void {
      history.replaceState(
        _omit(this.queryParams(window.location.search), key),
        document.title,
        this.removeQueryParam(window.location.search, key)
      );
    },
    replaceUrlState: function(key: string, value: string): void {
      history.replaceState(
        _extend(this.queryParams(window.location.search), {key: value}),
        document.title,
        this.setQueryParam(window.location.search, key, value)
      );
    }
  },
}
</script>
