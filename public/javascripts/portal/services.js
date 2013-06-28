portal.factory('myPaginationService', function($rootScope) {
    var paginationService = {};
    
    paginationService.paginationDatas = {'current' : 1, 'max': 5, 'num': false};

    paginationService.prepForBroadcast = function(msg) {
        this.paginationDatas = msg;
        this.broadcastItem();
    };

    paginationService.broadcastItem = function() {
        $rootScope.$broadcast('handlePaginationBroadcast');
    };
	
    paginationService.changePage = function(page) {
        this.page = page;
        this.broadcastBottomItem();
    };

    paginationService.broadcastBottomItem = function() {
        $rootScope.$broadcast('handlePaginationBottomBroadcast');
    };
    return paginationService;
}).factory('myBasketService', function($rootScope) {
    var basketservice = {};
    
    basketservice.toBasket = {};

    basketservice.add = function(item) {
		// console.log(this);
		this.toBasket = item;
        //this.list.push(item);
        this.broadcastBasket();
    };
	
    basketservice.broadcastBasket = function() {
        $rootScope.$broadcast('handleBasketBroadcast');
    };
	
    return basketservice;
}).factory('Item', function($http){
	var Item = {}
	
	Item.query = function(type, item) {
		console.log(type);
		return $http.get('./api/'+type+'/'+item).success(function(data) {
			Item.data = data;
			if(type == "repository" && (data.relationships.describes[0].relationships.hasAddress[0]))
			{
				console.log("Getting Address");
				address = data.relationships.describes[0].relationships.hasAddress[0].data;
			/*
				format=[html|xml|json]
				street=<housenumber> <streetname>
				city=<city>
				county=<county>
				state=<state>
				country=<country>
				postalcode=<postalcode>
			*/
				$http.get('http://nominatim.openstreetmap.org/search/?format=json&street='+address.street+'&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
					if(data[0])
					{
						Item.geoloc = data[0];
						console.log(Item.address);
					}
				});
			}
		});
	}
	
	return Item;
}).factory('Map', function($http, $rootScope){
	var Map = {}
	if(!$rootScope.mapMarkers)
	{
		$rootScope.mapMarkers = [];
	}
	//<--- Geolocation
	Map.getLocation = function () {
		try {
			if (typeof navigator.geolocation === 'undefined'){
				gl = google.gears.factory.create('beta.geolocation');
			} else {
				gl = navigator.geolocation;
			}
		} catch(e) {}

		if (gl) {
			gl.getCurrentPosition(Map.getPosition, Map.getError);
		} else {
			alert("Geolocation services are not supported by your web browser.");
		}
	}
	
	Map.getPosition = function(position)
	{
		$rootScope.position = position;
		Map.broadcastMapLocation();
		console.log($rootScope.position);
	}
	Map.getError = function(position) {
		console.log(position);
	}
	
	Map.getLocation();
	
    Map.broadcastMapLocation = function() {
        $rootScope.$broadcast('broadcastMapLocation');
    };
	//Geolocation --->
	
	//<--- Recenter
    Map.broadcastMapCenter = function() {
        $rootScope.$broadcast('broadcastMapCenter');
    };
	Map.reCenter = function(data) {
		//{lat: data.lat, lng: data.lon}
		console.log(data);
		$rootScope.mapCenter = {lat: data.lat, lng: data.lng};
		Map.broadcastMapCenter();
		//panTo( <LatLng> latlng,
	}
	
	//Recenter --->
	//Markers
    Map.broadcastNewMapMarker = function() {
        $rootScope.$broadcast('broadcastNewMapMarker');
    };
	Map.returnLonLag = function (data, title, id) {
		if(data[0])
		{
			$rootScope.mapMarkers = {lat: data[0].lat, lng: data[0].lon, title: title, id:id};
			Map.broadcastNewMapMarker();
			//Map.reCenter(data[0]);
		}
	}
	Map.addMarker = function(item, title, id) {
		if(item.relationships.describes[0].relationships.hasAddress)
		{
			address = item.relationships.describes[0].relationships.hasAddress[0].data;
			/*
				format=[html|xml|json]
				street=<housenumber> <streetname>
				city=<city>
				county=<county>
				state=<state>
				country=<country>
				postalcode=<postalcode>
			*/
			$http.get('http://nominatim.openstreetmap.org/search/?format=json&street='+address.street+'&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
				if(data[0])	{
					Map.returnLonLag (data, title, id);
				} else {
					$http.get('http://nominatim.openstreetmap.org/search/?format=json&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
						if(data[0])	{
							Map.returnLonLag (data, title, id);
						}
					});
				}
				
			});
		}
	}
	
	return Map;
}); 