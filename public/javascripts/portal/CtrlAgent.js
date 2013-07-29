var Agent = portal.controller('AgentCtrl', ['$scope', 'Item', '$http', '$filter', '$rootScope', function($scope, $item, $http, $filter, $rootScope) {
/*
	Object model :
	item {
		raw : Raw json data,
		descId : id of chosen description,
		desc : description,
		pictures: retrieve picture from wikimedia
	}
	ui.blocks { } //Block for UI
*/


	$scope.item = { raw : $item.data };
	$scope.item.descId = false;
	$scope.item.desc = $filter("descLang")($scope.item.raw.relationships.describes, false, {"id" : $scope.descId})[0];
	console.log($scope.item.desc);
	
	
	
	// Wikimedia image api url request http://en.wikipedia.org/w/api.php?action=query&list=allimages&aiprop=url&format=json&ailimit=5&aifrom=Georgi%20Dimitrov&aiprop=comment
	/*
	$http.get('http://en.wikipedia.org/w/api.php?action=query&list=allimages&aiprop=url&format=json&ailimit=5&aifrom='+$scope.item.desc.data.name+'&aiprop=comment', {headers:  {'userAgent': 'portalTestEHRI/1.1 (http://ehri.com; ehri@ehri.com) AngularHS/1.4'}}).success(function(data) {
		$scope.item.pictures = data.query.allimages[0];
	}).error(function (data) {
		console.log("Error loading picture from WikiMedia");
	});
	*/
	
	$scope.ui = { 
		blocks : {
			functions : {
				toggleHide : function (id) {
					if(id) {
						$scope.ui.blocks.list[id].hidden = !$scope.ui.blocks.list[id].hidden;
						$rootScope.$broadcast('ui.blocks.functions.toggleHide.'+id);
					} else {
						angular.forEach($scope.ui.blocks.list, function(value, key){
							$scope.ui.blocks.list[key].hidden = true;
							$rootScope.$broadcast('ui.blocks.functions.toggleHide.'+key);
						});
					}
				}, // End toggleHide
				toggleClose : function (id) {
					if(id) {
						$scope.ui.blocks.list[id].closed = !$scope.ui.blocks.list[id].closed;
						$rootScope.$broadcast('ui.blocks.functions.toggleClose.'+id);
					} else {
						console.log("toggleHide all");
						angular.forEach($scope.ui.blocks.list, function(value, key){
							$scope.ui.blocks.list[key].closed = true;
							$rootScope.$broadcast('ui.blocks.functions.toggleClose.'+key);
						});
					}
				} // End toggleHide
			},
			list : {
				
			}
		} 
	};
}]);

Agent.resolveAgent = {
	itemData: function($route, Item) {
		console.log($route.current.params.itemID);
		var result = Item.query("historicalAgent", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 