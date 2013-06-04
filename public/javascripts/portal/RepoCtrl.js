portal.controller('RepoCtrl', ['$scope', '$service', 'item', function($scope, $service, $item) {

}]);

RepoCtrl.resolve = {
	phones: function($route, Phone) {
		return Phone.query();
	},
	delay: function($q, $defer) {
		var delay = $q.defer();
		$defer(delay.resolve, 1000);
		return delay.promise;
	}
} 