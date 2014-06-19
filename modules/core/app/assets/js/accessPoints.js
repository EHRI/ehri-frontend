$(document).ready(function() {
	/* Search and URLS	*/
	$search = function() {
      var search = function(types, page, callback) {
        var params = "?limit=10&q=%QUERY";
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
      	$types = element.parent().find(".type.btn-primary")
      	if($types === "undefined" || $types.length == 0) {
       		return []	
      	} else {
      		data = []
      		$types.each(function () {
      			data.push($(this).val())
      		})
      		return data
      	}
      };
      return {
        search: function(element, page) {
          return search(this.limitTypes(element), page);
        },
        limitTypes : limitTypes,
        filter: function(type, searchTerm, page, callback) {
          return search(this.limitTypes(type), (searchTerm || "PLACEHOLDER_NO_RESULTS"), page, callback);
        },

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

    $service = {
        get: jsRoutes.controllers.core.Application.get,
        getItem: jsRoutes.controllers.core.Application.getType,
        filter: jsRoutes.controllers.core.SearchFilter.filter,
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

    /* MODEL APPEND */
    $appends = function(elem, name, id) {
    	$accesslist = elem.parents(".accessPointList");
    	$target = $accesslist.find(".append-in")
    	var $model = $accesslist.find(".model")


    }
	/* Triggers */
	$(".add-access-toggle").on("click", function(e) {
		e.preventDefault();
		$(this).next().toggle();
	});
	$(".type:not([disabled])").on("click", function(e) {
		e.preventDefault();
		$(this).toggleClass("btn-primary")
	});

	/* Click on result */
	$(".input-group").on("click", ".add-access-element", function(e) {
		e.preventDefault()
		var $elem = $(this),
			$id = $elem.data("target"),
			$name = $elem.data("name");

	})

	/* Search input */
	$(".quicksearch").each(function() {
		$quicksearch = $(this);
		var $url = $search().search($quicksearch);
		var $quicksearchBH = new Bloodhound({
		                      datumTokenizer: function (d) {
		                            return Bloodhound.tokenizers.whitespace(d); 
		                      },
		                      queryTokenizer: Bloodhound.tokenizers.whitespace,
		                      remote: {
		                        url : $url,
		                        filter : function(parsedResponse) {
		                          var result = [];
		                          var alreadyResult = [];

		                          for (var i=0; i<parsedResponse.items.length; i++) {
		                            //Need to check if item not already in the db
		                            if($.inArray( parsedResponse.items[i].name , alreadyResult) === -1) {
		                              result.push({
		                              	id : parsedResponse.items[i].id,
		                                name: parsedResponse.items[i].name,
		                                value: parsedResponse.items[i].name,
		                                type : parsedResponse.items[i].type,
		                                parent : parsedResponse.items[i].parent
		                              });
		                              alreadyResult.push(parsedResponse.items[i][1]);
		                            }
		                          }
		                          return result;
		                        }
		                      }
		                    });
		$quicksearchBH.initialize();
		var $quicksearchTemplate = Handlebars.compile('<a class="add-access-element" data-name="{{name}}" data-target="{{id}}">{{name}} <span class="badge pull-right">{{parent}} {{type}}</span></a>');

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
	})
});