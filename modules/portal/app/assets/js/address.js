jQuery(document).ready(function ($) {
  var geoNamesUsername = 'EhriAdmin';

  var remotes = {
    city: {
      template: Handlebars.compile('<div><b>{{name}}</b><span class="text-muted"> - {{countryCode}} <small>({{adminName}})</small></span></div>'),
      selected: function (el, data) {
        form = el.parents(".address-form");
        form.find("[name$='countryCode']").select2("val", data.countryCode);
        form.find("[name$='firstdem']").val(data.adminName);
      },
      BH: new Bloodhound({
        datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name'),
        queryTokenizer: Bloodhound.tokenizers.whitespace,
        identify: function (obj) {
          return obj.geonameId
        },
        remote: {
          url: 'https://secure.geonames.org/searchJSON?name_startsWith=%QUERY&maxRows=10&username=' + geoNamesUsername + '&lang=en&featureClass=P&style=long',
          wildcard: "%QUERY",
          transform: function (response) {
            return response.geonames.map(function (item) {
              return {
                name: item.name,
                value: item.name,
                geonameId: item.geonameId,
                countryCode: item.countryCode,
                lat: item.lat,
                lng: item.lng,
                bbox: item.bbox,
                adminName: item.adminName1
              };
            });
          }
        }
      })
    }
  }

  function initTypeahead(that) {
    var remoteName = that.attr('data-remote');
    if (remotes[remoteName]) {
      that.typeahead({
            highlight: false
          },
          {
            name: remoteName,
            source: remotes[remoteName].BH,
            display: 'name',
            templates: {
              suggestion: remotes[remoteName].template
            }
          }
      ).bind('typeahead:select', function (e, data) {
        var elem = $(this);
        remoteName = elem.attr('data-remote');
        remotes[remoteName].selected(elem, data);
      });
    }
  }

  /**
   * Initialize typeahead.js
   */
  $('.typeahead').each(function () {
    initTypeahead($(this));
  });

  $(document).on("inlineFormset:added", function (e) {
    $(".typeahead", e.target).each(function () {
      initTypeahead($(this));
    })
  });
});