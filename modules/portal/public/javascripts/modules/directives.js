portal.directive('whenScrolled', function ($window) {
    return function(scope, element, attrs) {
        angular.element($window).bind("scroll", function() {
		
			var bottomPos = parseInt(element[0].offsetTop) + parseInt(element[0].offsetHeight) + 95;
			if (document.documentElement.scrollTop) { var currentScroll = document.documentElement.scrollTop; } else { var currentScroll = document.body.scrollTop; }
			var totalHeight = document.body.offsetHeight;
			var visibleHeight = document.documentElement.clientHeight;
			
			// Particularities : we have margin on top and bot
			//Bottom = 65
			//Top = 95
			
			//Total height of doc = (currentScroll + visibleHeight + 150)
			//Div's Bottom position = (element[0].offsetTop + element[0].offsetHeight)
			//Take care of calling it in angular.element. In any other case you would have its height before datas has been loaded
			
			var currentScrollHeight = currentScroll + visibleHeight + 95;
             if (bottomPos  <= currentScrollHeight) {
				scope.$apply(attrs.whenScrolled);
             }
        });
    }
}).directive("drop", ['$rootScope', function($rootScope) {
  
  function dragEnter(evt, element, dropStyle) {
    evt.preventDefault();
    element.addClass(dropStyle);
  };
  function dragLeave(evt, element, dropStyle) {
    element.removeClass(dropStyle);
  };
  function dragOver(evt) {
    evt.preventDefault();
  };
  function drop(evt, element, dropStyle) {
    evt.preventDefault();
    element.removeClass(dropStyle);
  };
  
  return {
    restrict: 'A',
    link: function(scope, element, attrs)  {
      scope.dropData = scope[attrs["drop"]];
      scope.dropStyle = attrs["dropstyle"];
      element.bind('dragenter', function(evt) {
        dragEnter(evt, element, scope.dropStyle);
      });
      element.bind('dragleave', function(evt) {
        dragLeave(evt, element, scope.dropStyle);
      });
      element.bind('dragover', dragOver);
      element.bind('drop', function(evt) {
        drop(evt, element, scope.dropStyle);
        $rootScope.$broadcast('ui.dom.drop', $rootScope.draggedElement, scope.dropData);
      });
    }
  }
}]).directive("drag", ["$rootScope", function($rootScope) {
  
  function dragStart(evt, element, dragStyle) {
    element.addClass(dragStyle);
    evt.dataTransfer.setData("id", evt.target.id);
    evt.dataTransfer.effectAllowed = 'move';
  };
  function dragEnd(evt, element, dragStyle) {
    element.removeClass(dragStyle);
  };
  
  return {
    restrict: 'A',
    link: function(scope, element, attrs)  {
      attrs.$set('draggable', 'true');
      scope.dragData = scope[attrs["drag"]];
      scope.dragStyle = attrs["dragstyle"];
      element.bind('dragstart', function(evt) {
        $rootScope.draggedElement = scope.dragData;
        dragStart(evt, element, scope.dragStyle);
      });
      element.bind('dragend', function(evt) {
        dragEnd(evt, element, scope.dragStyle);
      });
    }
  }
}]).directive("rud", [function() {
	return {
        restrict: 'E',
        replace: true,
		transclude: true,
		scope: {desc: '=src', colorclass:'='},
        templateUrl: ANGULAR_ROOT + "/view/directive-view/repoFields.html",
		link: function(scope,element, attrs) {
			scope.$watch('desc', function(walks) {
				console.log(scope.desc);
			});
		}
	}
}]).directive("dud", [function() {
	return {
        restrict: 'E',
        replace: true,
		transclude: true,
		scope: {desc: '=src', colorclass:'='},
        templateUrl: ANGULAR_ROOT + "/view/directive-view/descriptionFields.html",
		link: function(scope,element, attrs) {
			scope.$watch('desc', function(walks) {
				console.log(scope.desc);
			});
		}
	}
}]).directive("simpleBlock", ['$rootScope', function($rootScope) {
	return {
		restrict: 'C', 
		replace: true,
		transclude: true,
		scope: { title:"@" },
		templateUrl: ANGULAR_ROOT + "/view/directive-view/simple-block.html"
	}
	
}]).directive("documentBlock", ['$rootScope', function($rootScope) {
	return {
		restrict: 'C', 
		replace: true,
		transclude: true,
		scope: { itemtitle:"=", itemid:'=', show:'=', show:'=', reduce:'='},
		templateUrl: ANGULAR_ROOT + "/view/directive-view/ui-blocks.html",
		link: function($scope,$element, $attrs) {
				console.log($scope.show);
				if(!isEmptyObject($scope.show)) {
					//Id for element
					$scope.id = $attrs.identifier + "-" + $scope.itemid;
					
					//Get reduce state (ie. for other properties) for auto closed 
					if($attrs.reduce) {
						$scope.closed = true;
					} else { 
						$scope.closed = false;
					}
					
					//Merging area name with title of object
					$scope.title = $attrs.title + " : " + $scope.itemtitle;
					
					//If $parent used, and not $parent.$parent
					if($attrs.level) {
						$scope.$parent.ui.blocks.list[$scope.id] = {
							legend: $scope.title, 
							hidden: false, 
							closed:$scope.closed 
						};
						
						$scope.close = function() {
							$scope.$parent.ui.blocks.list[$scope.id].closed = !$scope.$parent.ui.blocks.list[$scope.id].closed;
							$scope.closed = $scope.$parent.ui.blocks.list[$scope.id].closed;
						}
						$scope.hide = function() {
							$scope.$parent.ui.blocks.list[$scope.id].hidden = !$scope.$parent.ui.blocks.list[$scope.id].hidden;
							$scope.hidden = $scope.$parent.ui.blocks.list[$scope.id].hidden;
						}
						$scope.$on('ui.blocks.functions.toggleClose.'+$scope.id, function() {
							$scope.closed = $scope.$parent.ui.blocks.list[$scope.id].closed;						
						});
						$scope.$on('ui.blocks.functions.toggleHide.'+$scope.id, function() {
							$scope.hidden = $scope.$parent.ui.blocks.list[$scope.id].hidden;						
						});
						
					} else {
						$scope.$parent.$parent.ui.blocks.list[$scope.id] = {
							legend: $scope.title, 
							hidden: false, 
							closed:$scope.closed 
						};
						
						
						$scope.close = function() {
							$scope.$parent.$parent.ui.blocks.list[$scope.id].closed = !$scope.$parent.$parent.ui.blocks.list[$scope.id].closed;
							$scope.closed = $scope.$parent.$parent.ui.blocks.list[$scope.id].closed;
						}
						$scope.hide = function() {
							$scope.$parent.$parent.ui.blocks.list[$scope.id].hidden = !$scope.$parent.$parent.ui.blocks.list[$scope.id].hidden;
							$scope.hidden = $scope.$parent.$parent.ui.blocks.list[$scope.id].hidden;
						}
						$scope.$on('ui.blocks.functions.toggleClose.'+$scope.id, function() {
							$scope.closed = $scope.$parent.$parent.ui.blocks.list[$scope.id].closed;						
						});
						$scope.$on('ui.blocks.functions.toggleHide.'+$scope.id, function() {
							$scope.hidden = $scope.$parent.$parent.ui.blocks.list[$scope.id].hidden;						
						});
					}
				} else {
					$scope.show = false;
					return true;
				}
			
		}
	}
}]).directive("annotate", ['$rootScope', function($rootScope) {
  function getSelected(evt) {
	if (window.getSelection) {
		ret =  window.getSelection().toString();
	} else if (document.selection) {
		ret = document.selection.createRange().text;
	}
	if(ret) { return ret; }
	else { return false; }
  };
  
  return {
    restrict: 'A',
    link: function(scope, element, attrs)  {
      scope.annotateData = scope[attrs["annotate"]];
      element.bind('mouseup', function(evt) {
        scope.selectedText = getSelected(evt);
		//console.log(scope.selectedText);
		//console.log(scope.annotateData);
		if(scope.selectedText != false)
		{
			$rootScope.$broadcast('ui.annotation.get', scope.annotateData, scope.selectedText);
		}
      });
    }
  }
}]).directive('sap', ['$rootScope', 'Map', function($rootScope, $map){
    return {
        restrict: 'E',
        replace: true,
        template: '<div></div>',
        link: function(scope, element, attrs) {
			$rootScope.map = {
				functions : {
					create : function() {	//Create the map element
						$rootScope.map.ui = L.map(attrs.id, {
							center: [$rootScope.map.options.lat, $rootScope.map.options.lon],
							zoom: $rootScope.map.options.zoom
						});
						$rootScope.map.ui.whenReady(function() {	
							L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', { maxZoom: 18 }).addTo($rootScope.map.ui);
							
							$rootScope.map.options.on = true;
							$rootScope.$broadcast('ui.map.ready');
							
							//Debug
							$rootScope.map.ui.on('movestart', function(e) {
								console.log(e);
							});
						});
						
						//Debug functions
						//console.log($rootScope.map.ui.getCenter());
						//console.log([$rootScope.map.options.lat, $rootScope.map.options.lon]);
						// ??? if(mapOptions.points) { mapFunctions.updatePoints(mapOptions.points); }
					},
					init : function() {
						if(scope.geoloc) {
							$rootScope.map.options.lat = scope.geoloc.lat;
							$rootScope.map.options.lon = scope.geoloc.lon;
							$rootScope.map.options.zoom = 6;
							$rootScope.map.options.points = [{lat: lat, lng: lon}];
						}
						else {
							//Default values
							$rootScope.map.options.lat = 49.32512199104001;
							$rootScope.map.options.lon = 10.1513671875;
							$rootScope.map.options.zoom = 5;
						}
						
						this.create();
						
					},
					reset : function () {
						$rootScope.map.ui._resetView($rootScope.map.ui.getCenter(), $rootScope.map.ui.getZoom(), true);	
					}
				},
				options : {
					lat:false,
					lon:false,
					zoom:false,
					on:false
				},
				markers : {
					raw : {},
					array : []
				}
			}
			
			
						
           //Add marker to layer on broadcasting a new one
            $rootScope.$on('ui.map.marker.new', function() {
				marker = $rootScope.mapMarkers;
				//Marker Leaflet version
				scope.mapMarkers.markers[marker.id] = {
					leaflet: L.marker([marker.lat, marker.lng], {title: marker.title}).bindPopup(marker.title), 
					data: {title: marker.title, id:marker.id, lat:marker.lat, lng:marker.lng}
				};
				mapFunctions.updateLayers();
            });
			
			//Center on broadcast
			$rootScope.$on('ui.map.reset', function () {
				$rootScope.map.functions.reset();	
			});
			$rootScope.$on('ui.map.marker.ready', function () {
				$rootScope.map.functions.reset();	
			});
			$rootScope.$on('ui.map.marker.center', function () {
				if($rootScope.map.options.on) {
					$rootScope.map.ui.setView([$rootScope.map.options.lat, $rootScope.map.options.lon], 6);	
				}
			});
			
			//Init stuf
			//if($rootScope.map.options.lat)	{	//If we already have position
			$rootScope.map.functions.init();
			/*} else {	//If not
				$rootScope.$on('ui.map.init', function () { 
				});
			}*/
        }
    };
}]);