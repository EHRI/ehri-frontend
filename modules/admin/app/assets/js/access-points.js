$(document).ready(function () {

  "use strict";

  var ajaxHeaders = {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  };

  // Search and URLS
  var $search = function () {
    var page = function ($element) {
      var $container = $element.parents(".input-group").first(),
          $page = $container.find(".pages:not(.change-page):visible"),
          $actual = parseInt($page.find(".page").text());

      if (typeof $page !== "undefined" && parseInt($actual) != 0) {
        return $actual
      } else {
        return 1
      }
    };

    var search = function (types, holders, searchTerm, page, callback) {
      var params = "?limit=10&q=" + searchTerm;
      if (types && types.length > 0) {
        if (Array.isArray(types)) {
          params = params + "&" + (types.map(function (t) {
                return "st[]=" + t
              }).join("&"));
        } else {
          params = params + "&st[]=" + types;
        }
      }
      if (holders && holders.length > 0) {
        if (Array.isArray(holders)) {
          params = params + "&f[]=holderId:(" + holders.join(" ") + ")";
        } else {
          params = params + "&f[]=holderId:" + holders;
        }
      }
      if (page > 0) {
        params = params + "&page=" + page;
      }
      return $service.filter().url + params;
    };

    var limitTypes = function (element) {
      return element.parents(".input-group")
          .find(".type-filters > .type.active")
          .map(function(i, elem) { return $(elem).data("value"); })
          .get();
    };

    var limitHolders = function (element) {
      return element.parents(".input-group")
          .find(".holder-filters > .holder.active")
          .map(function(i, elem) { return $(elem).data("value"); })
          .get();
    };

    var searchTerm = function (element) {
      var $name = element.attr("name");
      return $(".tt-input[name='" + $name + "']").val()
    };

    return {
      searchTerm: function (element) {
        return searchTerm(element);
      },
      page: function (element) {
        return page(element);
      },
      search: function (element) {
        return search(this.limitTypes(element), this.limitHolders(element), this.searchTerm(element), this.page(element));
      },
      limitTypes: limitTypes,
      limitHolders: limitHolders,
      detail: function (type, id, callback) {
        return $.get($service.getItem(type, id).url, {
          headers: ajaxHeaders
        }, callback);
      }
    };
  };

  var $service = {
    filter: adminJsRoutes.controllers.admin.SearchFilter.filterItems,
    get: adminJsRoutes.controllers.admin.Data.getItem,
    getItem: adminJsRoutes.controllers.admin.Data.getItemType,
    createLink: adminJsRoutes.controllers.units.DocumentaryUnits.createLink,
    createMultipleLinks: adminJsRoutes.controllers.units.DocumentaryUnits.linkMultiAnnotatePost,
    createAccessPoint: adminJsRoutes.controllers.units.DocumentaryUnits.createAccessPoint,
    getAccessPoints: adminJsRoutes.controllers.units.DocumentaryUnits.getAccessPointsJson,
    deleteLink: adminJsRoutes.controllers.units.DocumentaryUnits.deleteLink,
    deleteAccessPoint: adminJsRoutes.controllers.units.DocumentaryUnits.deleteAccessPoint,
    deleteLinkAndAccessPoint: adminJsRoutes.controllers.units.DocumentaryUnits.deleteLinkAndAccessPoint,
    redirectUrl: function (id) {
      return adminJsRoutes.controllers.units.DocumentaryUnits.get(id).url;
    }
  };

  // Get list of access points
  var getAccessPointList = function () {
    var $item = $(".item-annotation-links"),
        $itemId = $item.data("id"),
        $descriptionId = $item.data("did"),
        $accessList = null;

    $service.getAccessPoints($itemId, $descriptionId).ajax({
      success: function (data) {
        for (var i in data) {
          if (data.hasOwnProperty(i)) {
            if (data[i].id === $descriptionId) {
              $accessList = data[i];
              break;
            }
          }
        }

        function hasTarget(item) {
          return typeof item.target !== "undefined";
        }

        // If we have an access point list
        if ($accessList) {
          // For each access point type
          $.each($accessList["data"], function (index, arr) {
            //We get temporary element and the model as well which we clone
            var $container = $(".accessPointList." + arr["type"] + " .item-annotation-links"),
                $model = $container.find(".access-saved.model").clone().removeClass("model"),
                $notmodel = $container.find(".access-saved:not(.model)");

            // Remove old items
            $notmodel.remove();
            // For each element, we create and prepend a new line
            $.each(arr["data"], function (index, a) {
              var $element = $model.clone();
              $element.attr("id", a.accessPoint.id);
              if (typeof a.link !== "undefined") {
                $element.data("link", a.link.id)
              }
              $element.find(".access-saved-name").text(a.accessPoint.name);
              if (hasTarget(a)) {
                $element.find(".access-saved-name").attr("href", $service.getItem(a.target.type, a.target.id).url)
              } else {
                $element.find(".access-saved-name").removeAttr("href").css("color", "#000000");
              }
              if (a.accessPoint.description) {
                $element.find(".access-saved-description").html("<p>" + a.accessPoint.description + "</p>")
              }

              // Remove confirmation
              var $deleteButton = $element.find(".access-saved-delete");
              $deleteButton.confirmation({
                title: "Delete link for this access point ?",
                singleton: true,
                popout: true,
                placement: "bottom",
                trigger: "click",
                onConfirm: function () {
                  var $route = !hasTarget(a)
                      ? $service.deleteAccessPoint($itemId, $descriptionId, a.accessPoint.id)
                      : $service.deleteLinkAndAccessPoint($itemId, $descriptionId, a.accessPoint.id, $element.data("link"));
                  var $modal = $(".waiting-modal").modal({
                    backdrop: true,
                    keyboard: false
                  });
                  $modal.modal("show");
                  $deleteButton.attr("disabled", true);
                  $route.ajax({
                    success: function (data) {
                      getAccessPointList();
                    },
                    complete: function () {
                      $modal.modal("hide");
                      $deleteButton.attr("disabled", false);
                    }
                  });
                },
                onCancel: function () {
                  $element.find(".access-saved-delete").confirmation("hide")
                }
              });
              // Finally append it
              $container.prepend($element)
            })
          })
        }
        return $accessList;
      }
    });
  };

  // Gather data about one access point
  var makeScope = function ($container) {
    var $element = $container.find(".element-name"),
        $item = $container.parents(".item-annotation-links").first();
    return {
      // Data for access point
      id: $item.data("id"),
      did: $item.data("did"),
      name: $element.text(),
      type: $container.parents(".new-access-point").first().data("type"),
      description: $container.find(".element-description").val(),
      // Data for link
      link: {
        target: $element.val(),
        name: $element.text(),
        targetType: $element.data("type"),
        type: "associative"
      },

      // FOR RETRIEVAL PURPOSE
      container: $container
    };
  };

  /**
   * Save the new access point data to the server, refreshing
   * the list of access points when done.
   * Scope is the object returned in makeScope
   */
  var nextNewAccesspoint = function ($scope, $accesspoints) {
    var $parent = $scope.container.parents(".accessPointList").first();
    $scope.container.remove();
    // Now that it is done, we get to the next if it exist
    if ($accesspoints.length > 0) {
      var $scope2 = makeScope($accesspoints.first()),
          $accessPointList = $accesspoints.slice(1);

      saveNewAccessPoint($scope2, $accessPointList);

    } else {
      getAccessPointList();
      // Show the Save and cancel buttons
      $parent.find(".submit-group").hide()
    }
  };

  var saveNewAccessPoint = function ($scope, $accesspoints) {
    $service.createAccessPoint($scope.id, $scope.did).ajax({
      data: JSON.stringify({
        name: $scope.name,
        accessPointType: $scope.type,
        isA: "AccessPoint",
        description: $scope.description
      }),
      headers: ajaxHeaders
    }).done(function (data) {
      if ($scope.link.targetType == null) {
        nextNewAccesspoint($scope, $accesspoints);
      } else {
        $service.createLink($scope.id, data.id).ajax({
          data: JSON.stringify({
            target: $scope.link.target,
            type: $scope.link.type,
            description: $scope.description
          }),
          headers: ajaxHeaders
        }).done(function (data) {
          nextNewAccesspoint($scope, $accesspoints);
        });
      }
    });
  };

  // Append an access point item
  var appends = function ($elem, name, id, did, type) {
    var $accessPointList = $elem.hasClass("accessPointList")
        ? $elem
        : $elem.parents(".accessPointList");
    $accessPointList.find(".form-control.quicksearch.tt-input").typeahead("val", "");
    var $target = $accessPointList.find(".append-in");
    var $model = $accessPointList.find(".element.model");
    var $element = $model.data("target", id).clone().removeClass("model").addClass("element");
    $element.find(".element-name").text(name).val(id).data("did", did).data("type", type);
    $target.append($element.show());

    // Show the Save and cancel buttons
    $accessPointList.find(".submit-group").show()
  };

  // Triggers
  $(".add-access-toggle").on("click", function (e) {
    e.preventDefault();
    $(this).next().toggle().find(".form-control.quicksearch.tt-input").val("").focus();
  });
  $(".type:not([data-disabled])").on("click", function (e) {
    e.preventDefault();
    $(this).toggleClass("active")
  });

  var addAccessPointFromExisting = function ($target, name, id, did, type) {
    var $elem = $target.parents(".accessPointList");
    appends($elem, name, id, did, type)
  };

  // Add a text-only access point
  $(".add-access-text").on("click", function (e) {
    e.preventDefault();
    var $accesslist = $(this).parents(".accessPointList"),
        $input = $accesslist.find(".form-control.quicksearch.tt-input"),
        $name = $input.val();

    appends($accesslist, $name, null, null, null)
  });

  // Save pending items
  $(".new-access-point .element-save").on("click", function (e) {
    e.preventDefault();
    var $form = $(this).parents(".new-access-point").first(),
        $accessPoints = $form.find(".append-in > .element:not(.model)");

    if ($accessPoints.length > 0) {
      var $scope = makeScope($accessPoints.first()),
          $accessPointList = $accessPoints.slice(1);
      saveNewAccessPoint($scope, $accessPointList);
    }
  });

  // Cancel pending items */
  $(".new-access-point .element-cancel").on("click", function (e) {
    var $parent = $(this).parents(".new-access-point").first(),
        $pending = $parent.find(".append-in > .element:not(.model)");
    $pending.remove();
    $parent.find(".submit-group").hide();
  });

  // Cancel a single pending item
  $(".append-in").on("click", ".cancel-item", function () {
    var $elem = $(this),
        $parent = $elem.parents(".element:not(.model)").first(),
        $list = $parent.parent();
    $parent.remove();
    if ($list.find(".element:not(.model)").length == 0) {
      $list.parent().find(".submit-group").first().hide();
    }
  });

  // Search input
  $(".quicksearch").each(function () {
    var $quicksearch = $(this);

    var $quicksearchBH = new Bloodhound({
      datumTokenizer: function (d) {
        return Bloodhound.tokenizers.whitespace(d);
      },
      queryTokenizer: Bloodhound.tokenizers.whitespace,
      remote: {
        elem: $quicksearch,
        url: $search().search($quicksearch),
        replace: function () {
          return $search().search(this.elem)
        },
        filter: function (parsedResponse) {
          var result = [],
              alreadyResult = [],
              $container = this.elem.parents(".input-group").first(),
              $pages = $container.find(".pages"),
              $page = $container.find(".pages:not(.change-page)");

          $pages.show();
          $page.find(".max").text(parsedResponse.numPages);
          $page.find(".page").text(parsedResponse.page);
          for (var i = 0; i < parsedResponse.items.length; i++) {
            // Need to check if item not already in the db
            if ($.inArray(parsedResponse.items[i].name, alreadyResult) === -1) {
              result.push({
                id: parsedResponse.items[i].id,
                name: parsedResponse.items[i].name,
                value: parsedResponse.items[i].name,
                type: parsedResponse.items[i].type,
                parent: parsedResponse.items[i].parent,
                did: parsedResponse.items[i].did
              });
              alreadyResult.push(parsedResponse.items[i][1]);
            }
          }
          return result;
        }
      }
    });
    $quicksearchBH.initialize();
    var $quicksearchTemplate = Handlebars.compile('<a class="add-access-element" data-type="{{type}}" data-did="{{did}}" data-name="{{name}}" data-target="{{id}}">{{name}} <span class="badge pull-right">{{parent}} {{type}}</span></a>');

    // Initialize typeahead.js
    $quicksearch.typeahead(
        null,
        {
          name: "quicksearch",
          source: $quicksearchBH.ttAdapter(),
          templates: {
            suggestion: $quicksearchTemplate
          }
        }
    ).keypress(function (e) {
          if (e.which == 13) {
            e.preventDefault();
          }
        }).on("typeahead:selected", function (event, selection) {
          addAccessPointFromExisting(
              $(event.target),
              selection.name,
              selection.id,
              selection.did,
              selection.type
          );
        });
  });

  $("ul.type-filters, ul.holder-filters").on("click", function (e) {
    e.stopPropagation()
  });

  $(".accessPointList").on("click", "[data-apply='confirmation']", function (e) {
    e.preventDefault();
  });

  $(".pages.change-page").on("click", function () {
    var $element = $(this),
        $container = $element.parents(".input-group").first(),
        $page = $container.find(".pages:not(.change-page)"),
        $max = parseInt($page.find(".max").text()),
        $actual = parseInt($page.find(".page").text()),
        $input = $container.find(".form-control.quicksearch.tt-input"),
        $val = $input.val(),
        $next = $actual;

    if ($element.find(".glyphicon-minus").length == 1 && $actual - 1 >= 1) {
      $next = $actual - 1;
    } else if ($element.find(".glyphicon-plus").length == 1 && $actual + 1 <= $max) {
      $next = $actual + 1;
    }

    if ($next != $actual) {
      $page.find(".page").text($next);
      $input.typeahead("val", "");
      $input.typeahead("val", $val);
    }
  });

  // Init trigger
  getAccessPointList()
});