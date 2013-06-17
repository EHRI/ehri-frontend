var Doc = portal.controller('DocCtrl', ['$scope', '$filter', '$location', '$routeParams', 'Item', function($scope, $filter, $location, $routeParams, $item) {
	$scope.item = $item.data;
	$scope.alt = {};
	//<-- Set id of desc
	if($location.search().description) {
		$scope.descId = $location.search().description;
	}
	$scope.$on('$routeUpdate', function(){
		if($location.search().description) {
			$scope.descId = $location.search().description;
			$scope.loadDesc();// Change desc
		}
	});
	// Set id of desc -->
	
	//<-- Select good desc 
	$scope.loadDesc = function() {
		if($scope.descId)
		{
			$scope.desc = $filter("descLang")($scope.item.relationships.describes, false, {"id" : $scope.descId})[0];
			$scope.alt = [];
		}
		else
		{
			$scope.desc = $filter("descLang")($scope.item.relationships.describes)[0];
			$scope.alt = [];
		}
		//console.log($scope.desc);
		//$scope.$apply();
	}
	// Select good desc -->
	
	//<-- No Desc ? GOT ONE !
	$scope.getAlt = function (check, path, ret)
	{
		if($scope.desc != undefined) {
			// console.log("-----------------------");
			// console.log("Check : "+check);
			// console.log($scope.desc.data[check]);
			// console.log($scope.alt[path]);
			// console.log("-----------------------");
			if($scope.desc.data[check] == undefined && !$scope.alt[path]) {
				var alt = $filter("descLang")($scope.item.relationships.describes, false, {"not" : $scope.descId, "property": path, "returnProp" : ret});
				$scope.alt[path] = alt;
				return alt;
			}
			else {
				return $scope.alt[path];
			}
		}
	}
	// No desc -->
	
	//<-- Date format
	$scope.formatDate = function(dateMs) {
		var more = function(X) {
			if(X >= 10)	{
				return X;
			}
			else {
				return "0"+X;
			}
		}
		var date = new Date(dateMs);
		var month = date.getMonth()+1;
		var day = date.getDate();
		var year = date.getFullYear();
		return more(month) + '-' + more(day) + '-' + year;
	}
	// Date format -->
	
	//<-- Load data 
	$scope.loadDesc();
	// Load data-->
}]);

Doc.resolve = {
	itemData: function($route, Item) {
		var result = Item.query("documentaryUnit", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 