$(document).ready(function() {
  var ajaxHeaders = {
  "ajax-ignore-csrf": true,
  "Content-Type": "application/json",
  "Accept": "application/json; charset=utf-8"
  };
  /* Search and URLS  */
  var $search = function() {
    var page = function($element) {

    var $container = $element.parents(".input-group").first(),
      $page = $container.find(".pages:not(.change-page):visible"),
      $actual = parseInt($page.find(".page").text());

        if(typeof $page !== "undefined" && parseInt($actual) != 0) {
          return $actual
        } else {
          return 1
        }
    }
      var search = function(types, searchTerm, page, callback) {
        var params = "?limit=10&q=" + searchTerm;
        if (types && types.length > 0) {
          if(Array.isArray(types)) {
            params = params + "&" + (types.map(function(t) { return "st[]=" + t }).join("&"));
          } else {
            params = params + "&st[]=" + types;
          }
        }
        if (page > 0) {
          params = params + "&page=" + page;
        }
        return $service.filter().url + params;
      };
      var limitTypes = function(element) {
        var $types = element.parents(".input-group").first().find(".type.active")
        if($types === "undefined" || $types.length == 0) {
          return [] 
        } else {
          data = []
          $types.each(function () {
            data.push($(this).data("value"))
          })
          return data
        }
      };
      var searchTerm = function(element) {
        var $name = element.attr("name");
        return $(".tt-input[name='" + $name + "']").val()
      }
      return {
        searchTerm : function(element) { return searchTerm(element); },
        page : function(element) { return page(element); },
        search: function(element) {
          return search(this.limitTypes(element), this.searchTerm(element), this.page(element));
        },
        limitTypes : limitTypes,
        detail: function(type, id, callback) {
          return $.get($service.getItem(type, id).url, {
            headers: {
              "Content-Type": "application/json",
              "Accept": "application/json; charset=utf-8"
            }
          }, function (data) {
            callback(data);
          });
        }
      };
    };

  var $service = {
        filter: jsRoutes.controllers.core.SearchFilter.filter,
        get: jsRoutes.controllers.admin.Admin.get,
        getItem: jsRoutes.controllers.admin.Admin.getType,
        createLink: jsRoutes.controllers.archdesc.DocumentaryUnits.createLink,
        createMultipleLinks: jsRoutes.controllers.archdesc.DocumentaryUnits.linkMultiAnnotatePost,
        createAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.createAccessPoint,
        getAccessPoints: jsRoutes.controllers.archdesc.DocumentaryUnits.getAccessPointsJson,
        deleteLink: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteLink,
        deleteAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteAccessPoint,
        deleteLinkAndAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteLinkAndAccessPoint,
        redirectUrl: function(id) {
          return jsRoutes.controllers.archdesc.DocumentaryUnits.get(id).url;
        }
  };

  /**
  * Get list of access points
  *
  */
  var getAccessPointList = function() {
    var $item = $(".item-annotation-links"),
      $itemId = $item.data("id"),
      $descriptionId = $item.data("did"),
      $accesslist= false;
    $service.getAccessPoints($itemId, $descriptionId).ajax({
      success: function(data) {
        for (var i in data) {
          if (data[i].id === $descriptionId) {
            $accesslist = data[i];
            break;
          }
        }
        //If we have an access point list
        if($accesslist) {
          //For each access point type
          $.each($accesslist["data"], function(index, arr) {
            //We get temporary element and the model as well which we clone
            var $container = $(".accessPointList."+arr["type"]+ " .item-annotation-links"),
              $model = $container.find(".access-saved.model").clone().removeClass("model"),
              $notmodel = $container.find(".access-saved:not(.model)")
            //We remove old iems
            $notmodel.remove();
            //For each element, we create and prepend a new line
            $.each(arr["data"], function(index, a) {
              var $element = $model.clone();
              $element.attr("id", a.accessPoint.id)
              if(typeof a.link !== "undefined") {
                $element.data("link", a.link.id)
              }
              $element.find(".access-saved-name").text(a.accessPoint.name)
              if(typeof a.target !== "undefined") {
                $element.find(".access-saved-name").attr("href", $service.getItem(a.target.type, a.target.id).url)
              } else {
                $element.find(".access-saved-name").removeAttr("href").css("color", "#000000");
              }
              if(a.accessPoint.description) {
                $element.find(".access-saved-description").html("<p>" + a.accessPoint.description + "</p>")
              }
              /* Remove confirmation */
              $element.find(".access-saved-delete").confirmation({
                title : 'Delete link for this access point ?',
                singleton: true,
                popout: true,
                placement: 'bottom',
                trigger: "click",
                onConfirm : function() {
                  if(a.target !== "undefined") {
                    var $route = $service.deleteAccessPoint($itemId, $descriptionId, a.accessPoint.id)
                  } else {
                    var $route = $service.deleteLinkAndAccessPoint($itemId, $descriptionId, a.accessPoint.id, $element.data("link"))
                  }
                  $route.ajax({
                    success: function(data) {
                      getAccessPointList();
                    }
                  });
                },
                onCancel : function () {
                  $element.find(".access-saved-delete").confirmation('hide')
                }
              })
              /* Finally append it*/
              $container.prepend($element)
            })  
          })
        }
        return $accesslist;
      }
    });
  };
  /**
  * Gather data about one access point
  *
  */
  var makeScope = function($container) {
    var $element = $container.find(".element-name"),
      $item = $container.parents(".item-annotation-links").first(),
      o = {
        /* Data for access point*/
        id : $item.data("id"),
        did : $item.data("did"),
        name : $element.text(),
        type : $container.parents(".new-access-point").first().data("type"),
        description: $container.find(".element-description").val(),
        /* Data for link */
        link : {
          target: $element.val(),
          name: $element.text(),
          targetType: $element.data("type"),
          type: "associative"
        },

        /* FOR RETRIEVAL PURPOSE */
        container: $container
      }
    return o;
  }

  /**
  * Save the new access point data to the server, refreshing
  * the list of access points when done.
  * Scope is the object returned in makeScope
  */

  var nextNewAccesspoint = function($scope, $accesspoints, $parent) {
    var $parent = $scope.container.parents(".accessPointList").first();
    $scope.container.remove();
    // Now that it is done, we get to the next if it exist
      if($accesspoints.length > 0) {
        var $scope2 = makeScope($accesspoints.first()),
          $accessPointList = $accesspoints.slice(1);

        saveNewAccessPoint($scope2, $accessPointList);

      } else {
        getAccessPointList();
        //Show the Save and cancel buttons
        $parent.find(".submit-group").hide()
    }
  }
  var saveNewAccessPoint = function($scope, $accesspoints) {
    $service.createAccessPoint($scope.id, $scope.did).ajax({
      data: JSON.stringify({
        name: $scope.name,
        accessPointType: $scope.type,
        isA: "relationship",
        description: $scope.description
      }),
      headers: ajaxHeaders
    }).done(function(data) {
      if($scope.link.targetType == null) {
        nextNewAccesspoint($scope, $accesspoints)
      } else {
        $service.createLink($scope.id, data.id).ajax({
          data: JSON.stringify({
            target: $scope.link.target,
            type: $scope.link.type,
            description: $scope.description
          }),
          headers: ajaxHeaders
        }).done(function(data) {
          nextNewAccesspoint($scope, $accesspoints)
        })
      }
    })
  };

    /* MODEL APPEND */
    var appends = function(elem, name, id, did, type) {
      if(!elem.hasClass("accessPointList")) {
        $accesslist = elem.parents(".accessPointList");
      } else {
        $accesslist = elem;
      }
      $accesslist.find(".form-control.quicksearch.tt-input").typeahead('val', "")
      $target = $accesslist.find(".append-in")
      var $model = $accesslist.find(".element.model")
      var $element = $model.data("target", id).clone().removeClass("model").addClass("element")
      $element.find(".element-name").text(name).val(id).data("did", did).data("type", type)
      $target.append($element.show())

      //Show the Save and cancel buttons
      $accesslist.find(".submit-group").show()
    }

    $(".append-in").on("click", ".btn-danger", function() {
      var $elem = $(this),
        $parent = $elem.parents(".element").first()

      $parent.remove()
    })

  /* Triggers */
  $(".add-access-toggle").on("click", function(e) {
    e.preventDefault();
    $(this).next().toggle().find(".form-control.quicksearch.tt-input").val("").focus();
  });
  $(".type:not([data-disabled])").on("click", function(e) {
    e.preventDefault();
    $(this).toggleClass("active")
  });

  /* Click on result */
  $(".input-group").on("click", ".tt-suggestion:has(.add-access-element)", function(e) {
    e.preventDefault()
    var $elem = $(this).find(".add-access-element"),
      $id = $elem.data("target"),
      $name = $elem.data("name"),
      $did = $elem.data("did"),
      $type = $elem.data("type");
    appends($elem, $name, $id, $did, $type)
  });

  $(".add-access-text").on("click", function(e) {
    e.preventDefault();
    var $accesslist = $(this).parents(".accessPointList"),
      $input = $accesslist.find(".form-control.quicksearch.tt-input");
      $name = $input.val();

    appends($accesslist, $name, null, null, null)
  })

  /* Save access point */
  $(".new-access-point .element-save").on("click", function(e) {
    e.preventDefault();
    var $form = $(this).parents(".new-access-point").first(),
      $accesspoints = $form.find(".append-in > .element:not(.model)"),
      $requests = [],
      $requests2 = []

    if($accesspoints.length > 0) {
      var $scope = makeScope($accesspoints.first()),
        $accessPointList = $accesspoints.slice(1)
      saveNewAccessPoint($scope, $accessPointList);
    }
  })

  /* Search input */
  $(".quicksearch").each(function() {
    $quicksearch = $(this);

    var $quicksearchBH = new Bloodhound({
                          datumTokenizer: function (d) {
                                return Bloodhound.tokenizers.whitespace(d); 
                          },
                          queryTokenizer: Bloodhound.tokenizers.whitespace,
                          remote: {
                            elem : $quicksearch,
                            url : $search().search($quicksearch),
                            replace : function() { 
                              return $search().search(this.elem) 
                            },
                            filter : function(parsedResponse) {
                              var result = [],
                                  alreadyResult = [];
                                  $container = this.elem.parents(".input-group").first(),
                                  $pages = $container.find(".pages"),
                    $page = $container.find(".pages:not(.change-page)");

                  $pages.show();
                  $page.find(".max").text(parsedResponse.numPages)
                  $page.find(".page").text(parsedResponse.page)
                              for (var i=0; i<parsedResponse.items.length; i++) {
                                //Need to check if item not already in the db
                                if($.inArray( parsedResponse.items[i].name , alreadyResult) === -1) {
                                  result.push({
                                    id : parsedResponse.items[i].id,
                                    name: parsedResponse.items[i].name,
                                    value: parsedResponse.items[i].name,
                                    type : parsedResponse.items[i].type,
                                    parent : parsedResponse.items[i].parent,
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

    /**
    * Initialize typeahead.js
    */
    $quicksearch.typeahead(
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
              e.preventDefault();
          }
      });
  });

  $(".dropdown-menu.filters").on("click",function(e){ e.stopPropagation() });

  $(".accessPointList").on("click", "[data-apply='confirmation']", function(e) {
    e.preventDefault();
  })

  $(".pages.change-page").on("click", function() {
    var $element = $(this),
      $container = $element.parents(".input-group").first(),
      $page = $container.find(".pages:not(.change-page)"),
      $max = parseInt($page.find(".max").text()),
      $actual = parseInt($page.find(".page").text()),
      $input = $container.find(".form-control.quicksearch.tt-input"),
      $val = $input.val(),
      $next = $actual;

    if($element.find(".glyphicon-minus").length == 1 && $actual - 1 >= 1) {
      $next = $actual -1;
    } else if ($element.find(".glyphicon-plus").length == 1 && $actual + 1 <= $max) {
      $next = $actual + 1;
    }

    if($next != $actual) {
      $page.find(".page").text($next);
      $input.typeahead('val', "");
      $input.typeahead('val', $val);
    }
  })


  /* Save link */
  $(".new-link .element-save").on("click", function(e) {
    e.preventDefault();
    var $form = $(this).parents(".new-access-point").first(),
      $links = $form.find(".append-in > .element:not(.model)"),
      $requests = [],
      $requests2 = []

    if($accesspoints.length > 0) {
      var $scope = makeLinkScope($links.first()),
        $linksList = $accesspoints.slice(1)
      saveNewLink($scope, $linksList);
    }
  })

  /* Init trigger */
  if(typeof LINK_ACTION === "undefined") {
    getAccessPointList()
  }
});