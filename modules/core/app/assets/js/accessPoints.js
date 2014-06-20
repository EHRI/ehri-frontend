$(document).ready(function() {
	var ajaxHeaders = {
	"ajax-ignore-csrf": true,
	"Content-Type": "application/json",
	"Accept": "application/json; charset=utf-8"
	};
	/* Search and URLS	*/
	var $search = function() {
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
      	$types = element.parents(".input-group").first().find(".type.btn-info")
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
      var searchTerm = function(element) {
      	var $name = element.attr("name");
      	return $(".tt-input[name='" + $name + "']").val()
      }
      return {
      	searchTerm : function(element) { return searchTerm(element); },
        search: function(element, page) {
          return search(this.limitTypes(element), this.searchTerm(element), page);
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
									$service.deleteLinkAndAccessPoint($itemId, $descriptionId, a.accessPoint.id, $element.data("link")).ajax({
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
	var saveNewAccessPoint = function($scope) {
		return $service.createAccessPoint($scope.id, $scope.did).ajax({
		  data: JSON.stringify({
		    name: $scope.name,
		    accessPointType: $scope.type,
		    isA: "relationship",
		    description: $scope.description
		  }),
		  headers: ajaxHeaders
		})
	};

    /* MODEL APPEND */
    var appends = function(elem, name, id, did, type) {
    	$accesslist = elem.parents(".accessPointList");
    	$target = $accesslist.find(".append-in")
    	var $model = $accesslist.find(".element.model")
    	var $element = $model.data("target", id).clone().removeClass("model").addClass("element")
    	$element.find(".element-name").text(name).val(id).data("did", did).data("type", type)
    	$target.append($element.show())
    }

    $(".append-in").on("click", ".btn-danger", function() {
    	var $elem = $(this),
    		$parent = $elem.parents(".element").first()

    	$parent.remove()
    })

	/* Triggers */
	$(".add-access-toggle").on("click", function(e) {
		e.preventDefault();
		$(this).next().toggle();
	});
	$(".type:not([disabled])").on("click", function(e) {
		e.preventDefault();
		$(this).toggleClass("btn-info")
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
	})

	/* Save access point */
	$(".new-access-point .element-save").on("click", function(e) {
		e.preventDefault();
		var $form = $(this).parents(".new-access-point").first(),
			$accesspoints = $form.find(".append-in > .element:not(.model)"),
			$requests = [],
			$requests2 = []

		$accesspoints.each(function() {
			var $scope = makeScope($(this))
			$requests.push(saveNewAccessPoint($scope).done(function(data) {
				$requests2.push($service.createLink($scope.id, data.id).ajax({
					data: JSON.stringify({
						target: $scope.link.target,
						type: $scope.link.type,
						description: $scope.description
					}),
					headers: ajaxHeaders
				}).done(function(data) {
					$scope.container.remove()
				}));
		  	}))
		})
		var defer = $.when.apply($, $requests);
		defer.done(function () {
			var defer2 = $.when.apply($, $requests2)
			defer2.done(function () {
				getAccessPointList();
			})
		})
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
	})

	/* Init trigger */
	getAccessPointList()
});