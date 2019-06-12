/**
 * Namespace for random helper functions
 * @type {*|{}}
 */
var EhriJs = EhriJs || {};

EhriJs.alert = function (msg, type) {
  var $flash = $(".flash.alert-" + type);
  if ($flash.length === 0) {
    $flash = $("<div class=\"flash alert alert-" + type + "\">" +
        "<button type=\"button\" class=\"close\" data-dismiss=\"alert\">&times;</button>" +
        "<span class=\"flash-content\">" + msg + "</span>" +
        "</div>");
    $flash.insertAfter("#header");
  } else {
    $flash.find(".flash-content").html(msg);
  }
};

EhriJs.alertSuccess = function (msg) {
  EhriJs.alert(msg, "success");
};

EhriJs.alertDanger = function (msg) {
  EhriJs.alert(msg, "danger");
};

EhriJs.alertInfo = function (msg) {
  EhriJs.alert(msg, "info");
};

// Loader widget
EhriJs.$loader = $("<div></div>")
    .addClass("text-center loader-container")
    .append($("<span></span>")
        .addClass("loader"));


jQuery(function ($) {

  "use strict";

  // Use jquery.placeholder to handle browsers that
  // don't support this natively
  $('input, textarea').placeholder();

// Facet label tooltips
  $(".facet-label").tooltip({
    placement: "top",
    delay: 500
  });

  var $dataPolicyWidget = $("#data-policy"),
      COOKIE_NAME = "ehriDataPolicy";

  function getDataPolicy() {
    return $.cookie(COOKIE_NAME) === 'true';
  }

  function hideDataPolicy() {
    $.cookie(COOKIE_NAME, true, {expires: 365, path: "/"});
    $dataPolicyWidget.modal("hide");
  }

  function isCrawler() {
    return /bot|googlebot|crawler|spider|robot|crawling/i.test(window.navigator.userAgent);
  }

  function showDataPolicy() {
    // Show layout banner
    $dataPolicyWidget.modal({
      backdrop: false,
      keyboard: false,
      show: true
    });

    $dataPolicyWidget.find("form").validate({
      submitHandler: hideDataPolicy,
      messages: {
        "agree-data-policy-terms": ""
      }
    });
  }

  if ($dataPolicyWidget.length > 0 && !getDataPolicy() && !isCrawler()) {
    showDataPolicy();
  }

  /*
  * Date search
  */
  $(".facet-date .date-submit").on("click", function (e) {
    $(e.target).closest("form").submit();
  });

  $(".facet-date .date").on("keyup", function (e) {
    e.preventDefault();
    var vals = {"begin": "", "end": ""},
        $dat = $(this),
        $parent = $dat.parents(".facet-date"),
        getDate = function (date) {
          if (date.length === 4 && !isNaN(parseInt(date))) {
            return date;
          }
          return ""
        },
        val = vals["begin"] + "-" + vals["end"];

    $(".date").each(function () {
      vals[$(this).data("target")] = getDate($(this).val());
    });

    if (val !== "-") {
      $parent.find(".target").val(val)
    }
  });

  /*
  *   History
  */
  // Show history content in modal popup
  $("#history-modal").on('show.bs.modal', function(e) {
    $(this).find(".modal-content").load(e.relatedTarget.href);

  });

  /*
  * Quick search
  */

  var $quicksearch = $("#quicksearch");
  if ($quicksearch.length) {
    var quicksearchBH = new Bloodhound({
      datumTokenizer: Bloodhound.tokenizers.obj.whitespace('name'),
      queryTokenizer: Bloodhound.tokenizers.whitespace,
      identify: function (obj) {
        return obj.href
      },
      remote: {
        url: jsRoutes.controllers.portal.Portal.filterItems().url + "?limit=5&st=DocumentaryUnit&st=Repository&st=Country&q=%QUERY",
        wildcard: "%QUERY",
        transform: function (response) {
          // Only show distinct names in the hint list...
          var result = [], seen = [];
          response.items.forEach(function(item) {
            if ($.inArray(item.name, seen) === -1) {
              result.push({
                name: item.name,
                value: item.name,
                href: jsRoutes.controllers.portal.Portal.browseItem(item.type, item.id).url
              });
              seen.push(item.name);
            }
          });
          return result;
        }
      }
    });

    /**
     * Initialize typeahead.js
     */
    $quicksearch.typeahead({
          hint: false,
          limit: Infinity
        }, {
          name: "quicksearch",
          source: quicksearchBH,
          display: 'name',
          templates: {
            suggestion: Handlebars.compile('<div><a href="{{href}}">{{name}}</a></div>')
          }
        }
    ).keypress(function (e) {
      if (e.which === 13) {
        $(this).parents("form").submit();
      }
    });
  }

  /*
    Loadings
  */
  $(document).on("click", "a.child-drop-down.closed", function (e) {
    e.preventDefault();
    var $this = $(this);
    $this.removeClass("closed").addClass("expanded");
    var $container = $("<div></div>");
    $container.insertAfter($this);
    $container.load(this.href);
  });

  $(document).on("click", "a.child-drop-down.expanded", function (e) {
    e.preventDefault();
    var $this = $(this);
    $this.next("div").remove();
    $this.removeClass("expanded").addClass("closed");
  });

  $(".content-load-toggle").click(function (e) {
    e.preventDefault();
    var $link = $(this),
        $text = $(".load-prompt", $link),
        $inverse = $link.data("inverse-text");
    var $container = $link.parent(),
        $data = $(".content-load-data", $container),
        $place = $(".content-load-placeholder", $container);
    if ($container.hasClass("loaded")) {
      $data.toggle(300);
      $place.toggle(300);
      $link.data("inverse-text", $text.text());
      $text.text($inverse);
    } else {
      $link.addClass("loading");
      $.get(this.href, function (data) {
        $data.append(data).show(300);
        $(document).trigger("description.change");
        $place.hide(300);
        $container.addClass("loaded");
        $link.removeClass("loading");
        $link.data("inverse-text", $text.text());
        $text.text($inverse);
      }, "html");
    }
  });


  $(".content-load a.load-in-view").on("visible", function (e) {
    var $link = $(this),
        $container = $link.parent(),
        $data = $(".content-load-data", $container);
    $link.addClass("loading");
    $data.load(this.href, function () {
      $link.removeClass("loading").addClass("loaded");
      if ($.fn.select2) {
        $data.find(".select2").each(function (i) {
          $(this).select2(select2Opts);
        });
      }
      $link.hide();
    });
  });

  function checkLoadVisibility() {
    $(".load-in-view").not(".loading, .loaded").each(function (i) {
      var $item = $(this);
      if (!$item.hasClass("loading")) {
        if (($(window).scrollTop() + $(window).height()) > $item.offset().top) {
          $item.trigger("visible");
        }
      }
    });
  }

  checkLoadVisibility();
  $(window).scroll(function (e) {
    checkLoadVisibility();
  });


  // Make global search box show up when focused...
  // This could be done with plain CSS if we didn't also
  // want to toggle the color of the search icon...
  $(".global-search #quicksearch").focusin(function () {
    $(this).parents(".global-search").removeClass("inactive");
  }).focusout(function () {
    $(this).parents(".global-search").addClass("inactive");
  });

  // Validate any forms with 'validate-form' class...
  if ($.fn.validate) {
    $(".validate-form").validate();
    $(document).ajaxComplete(function () {
      $(".validate-form").validate();
    });
  }
});
