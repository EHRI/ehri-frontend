/**
 * Namespace for random helper functions
 * @type {*|{}}
 */
var EhriJs = EhriJs || {};

EhriJs.alert = function(msg, type) {
  var $flash = $(".flash.alert-" + type);
  if ($flash.length == 0) {
    $flash = $("<div class=\"flash alert alert-" + type + "\">" +
        "<button type=\"button\" class=\"close\" data-dismiss=\"alert\">&times;</button>" +
        "<span class=\"flash-content\">" + msg + "</span>" +
        "</div>");
    $flash.insertAfter("#header");
  } else {
    $flash.find(".flash-content").html(msg);
  }
};

EhriJs.alertSuccess = function(msg) {
  EhriJs.alert(msg, "success");
};

EhriJs.alertDanger = function(msg) {
  EhriJs.alert(msg, "danger");
};

EhriJs.alertInfo = function(msg) {
  EhriJs.alert(msg, "info");
};


jQuery(function ($) {

  var $dataPolicyWidget = $("#data-policy"),
      COOKIE_NAME = "ehriDataPolicy";

  function getDataPolicy() {
    return $.cookie(COOKIE_NAME) === 'true';
  }

  function hasScreenSpaceForWidget() {
    // Goodness help us...
    return $(window).height() > 600;
  }

  function hideDataPolicy() {
    $.cookie(COOKIE_NAME, true, {expires: 365, path: "/"});
    $dataPolicyWidget.hide(200);
    $("footer").css("marginBottom", "");
  }

  function getWidgetPosition() {
    return $(window).height() - $dataPolicyWidget.outerHeight();
  }

  function showDataPolicy() {
    // Show layout banner
    $dataPolicyWidget.show().width(window.width)
        .css({
          position: "fixed",
          left: 0,
          top: getWidgetPosition()
        });

    $(window).resize(function(e) {
      $dataPolicyWidget.css({
        top: getWidgetPosition()
      })
    });

    $dataPolicyWidget.find("form").validate({
      submitHandler: hideDataPolicy,
      messages: {
        "agree-data-policy-terms": ""
      }
    });
    // Ensure footer has padding so all
    // content clears the banner.
    $("footer").css("marginBottom", $dataPolicyWidget.outerHeight());
  }

  if ($dataPolicyWidget.length > 0 && !getDataPolicy() && hasScreenSpaceForWidget()) {
    showDataPolicy();
  }


/*
*   History
*/
$(".panel-history").each(function() {
  //$(this).addClass("inactive");
  $(this).find(".panel-heading h3").append(
      $("<span />", {
        "class" : "expander pull-right glyphicon glyphicon-minus"
      }).on("click", function(e) {
          $(this).parents(".panel-history").toggleClass("inactive");
          $(this).toggleClass("glyphicon-plus").toggleClass("glyphicon-minus");
      })
    )
});

/*
* Quick search
*/


  var $quicksearch = $("#quicksearch");
  var $quicksearchBH = new Bloodhound({
                          datumTokenizer: function (d) {
                                return Bloodhound.tokenizers.whitespace(d); 
                          },
                          queryTokenizer: Bloodhound.tokenizers.whitespace,
                          remote: {
                            url : jsRoutes.controllers.core.SearchFilter.filter().url + "?limit=5&st[]=documentaryUnit&st[]=repository&st[]=historicalAgent&st[]=country&q=%QUERY",
                            filter : function(parsedResponse) {
                              var result = [];
                              var alreadyResult = [];

                              for (var i=0; i<parsedResponse.items.length; i++) {
                                //Need to check if item not already in the db
                                if($.inArray( parsedResponse.items[i][1] , alreadyResult) === -1) {
                                  result.push({
                                    name: parsedResponse.items[i][1],
                                    value: parsedResponse.items[i][1],
                                    href : jsRoutes.controllers.portal.Portal.browseItem(parsedResponse.items[i][2], parsedResponse.items[i][0]).url
                                  });
                                  alreadyResult.push(parsedResponse.items[i][1]);
                                }
                              }
                              return result;
                            }
                          }
                        });
  $quicksearchBH.initialize();
  var $quicksearchTemplate = Handlebars.compile('<a href="{{href}}">{{name}}</a>');

  /**
   * Initialize typeahead.js
   */
  $('#quicksearch').typeahead(
    null,
    {
      name: "quicksearch",
      source: $quicksearchBH.ttAdapter(),
      templates: {
        suggestion : $quicksearchTemplate
      }
    }
  ).keypress(function(e) {
    if(e.which == 13) {
        $(this).parents("form").submit();
    }
});
  //Need to reenable enter for getSearch

/*
  Search helpers
*/
$(".page-content").on("click", ".search-helper-toggle", function () {
  $("#search-helper").toggle();
});

$(".page-content").on("click", "#search-helper .close", function(e) {
  e.preventDefault();
  $("#search-helper").toggle();
});

/* 
  Loadings
*/
$loader = $( "<div></div>" ).addClass("text-center loader-container").append($("<span></span>").addClass("loader"));

  $(".content-load a.toggle").click(function(e){
    e.preventDefault();
    var $link = $(this),
        $text = $(".text", $link),
        $inverse = $link.data("inverse-text");
    var $container = $link.parent(),
        $data = $(".content-load-data", $container);
    if ($container.hasClass("loaded")) {
      $data.toggle(300);
      $link.data("inverse-text", $text.text());
      $text.text($inverse);
    } else {
      $link.addClass("loading");
      $.get(this.href, function(data) {
        $data.append(data).show(300);
        $container.addClass("loaded");
        $link.removeClass("loading");
        $link.data("inverse-text", $text.text());
        $text.text($inverse);
      }, "html")
    }
  });


  $(".content-load a.load-in-view").on("visible", function(e){
    var $link = $(this),
        $container = $link.parent(),
        $data = $(".content-load-data", $container);
    $link.addClass("loading");
    $data.load(this.href, function() {
      $link.removeClass("loading").addClass("loaded");
      $data.find("select").each(function(i) {
        $(this).select2(select2Opts);
        $link.hide();
      });
    })
  });

  function checkLoadVisibility() {
    $(".load-in-view").not(".loading, .loaded").each(function(i) {
      var $item = $(this);
      if(!$item.hasClass("loading")) {
        if (($(window).scrollTop() + $(window).height()) > $item.offset().top) {
          $item.trigger("visible")
        }
      }
    });
  }

  checkLoadVisibility()
  $(window).scroll(function(e) {
    checkLoadVisibility();
  });


  // Make global search box show up when focused...
  // This could be done with plain CSS if we didn't also
  // want to toggle the color of the search icon...
  $(".global-search #quicksearch").focusin(function() {
    $(this).parents(".global-search").removeClass("inactive");
  }).focusout(function() {
    $(this).parents(".global-search").addClass("inactive");
  });

  // Make top menu adhere to top of screen...
  var $pmenu = $(".nav-primary");
  var $smenu = $(".nav-secondary");
  var $marginTrick = $pmenu;
  var menuHeight = $smenu.outerHeight();
  var originalmarginTrick = $marginTrick.css("margin-bottom");
  $(window).scroll(function(e) {
    var menuHeight = $smenu.outerHeight();
    $("header#header").trigger("expander-remove");

    if ($(window).scrollTop() > ($pmenu.offset().top + $pmenu.outerHeight() + menuHeight)) {
      $smenu.addClass("float-nav").css({
        width: $(window).width()
      });
      $marginTrick.css("margin-bottom", menuHeight);
    } else {
      $smenu.removeClass("float-nav").css("width", $pmenu.outerWidth());
      $marginTrick.css("margin-bottom", originalmarginTrick);
    }
  });
  $(window).resize(function(e) {
    $("header#header").trigger("expander-remove");
    if($smenu.hasClass("float-nav")) {
      $smenu.css({
        width: $(window).width()
      });
    }
  });
  $("header#header").on("expander", function() {
    $("header#header .float-nav .more").parent().children("li").toggleClass("available");
  });
  $("header#header").on("expander-remove", function() {
    $("header#header .float-nav .more").parent().children("li").removeClass("available");
  });
  $("header#header").on("click", ".float-nav .more", function() {
    $("header#header").trigger("expander");
  });

  // jQuery history plugin... initialise
  // Bind to StateChange Event
  History.Adapter.bind(window,'statechange',function(){ // Note: We are using statechange instead of popstate
    var State = History.getState(); // Note: We are using History.getState() instead of event.state

    // TODO: Use...
  });

  // Integrate Bootstrap tab switching with the history plugin...
  $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
    // e.target // activated tab
    // e.relatedTarget // previous tab
    var t = $(e.target).data("tabidx"); // chop off #
    History.replaceState({tabState: t}, t, "?tab=" + t);
  });


  // Validate any forms with 'validate-form' class...
  $(".validate-form").validate();
  $(document).ajaxComplete(function () {
    $(".validate-form").validate();
    $("textarea.autosize").autosize();
  });




/**
 * Handle cookie pref loading/saving
 */
  window.Preferences = {
    update: function(prefsObj) {
      var prefs = prefsObj || {};
      // Fire and forget!
      jsRoutes.controllers.portal.Profile.updatePrefs().ajax({ data: prefsObj })
    },

    updateValue: function(key, value) {
      // fffff...
      var tmp = {};
      tmp[key] = value;
      return this.update(tmp);
    }
  };

/**
 * Handle updating global preferences when certain
 * items are clicked.
 */

  $(document).on("click", ".toggle-boolean-preference", function(e) {
    e.preventDefault();
    var $item = $(this),
        name = $item.data("preference-name"),
        value = $item.data("preference-value");
    $item
      .addClass("boolean-" + !value).removeClass("boolean-" + value)
      .data("preference-value", !value);
    Preferences.updateValue(name, !value);
    $(window.Preferences).trigger(name, !value);
  });

/**
 * Preference events
 */

  $(window.Preferences).bind("showUserContent", function(event, doShow) {
    $(".user-content").toggle(doShow);
  });
});
