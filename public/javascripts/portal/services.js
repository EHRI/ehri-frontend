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
	basketservice.content = [];

    basketservice.add = function(item) {
		// console.log(this);
		this.toBasket = item;
        //this.list.push(item);
        this.broadcastBasket();
		basketservice.content.push({id : item.id, type: item.type});
		console.log(basketservice.content);
		
    };
	
	basketservice.get = function() {
		return basketservice.content;
	};
	
    basketservice.broadcastBasket = function() {
        $rootScope.$broadcast('handleBasketBroadcast');
    };
	
    return basketservice;
}).factory('Item', function($http){
	var Item = {}
	
	Item.query = function(type, item, returned) {
		//console.log(type);
		return $http.get('./api/'+type+'/'+item).success(function(data) {
			if(returned) {
				return data;
			}
			else {
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
			}
		});
	}
	
	return Item;
}).factory('Map', function($http, $rootScope){
	var Map = {
		location : {	//Geolocation and location functions
			get: function () {	//Get geolocation of user through navigator, 
				try {
					if (typeof navigator.geolocation === 'undefined'){
						gl = google.gears.factory.create('beta.geolocation');
					} else {
						gl = navigator.geolocation;
					}
				} catch(e) {}

				if (gl) {
					gl.getCurrentPosition(Map.location.set, Map.error);
				} else {
					alert("Geolocation services are not supported by your web browser.");
				}
			},
			set: function(position) {	//Broadcast said position previously getPosition
				console.log(position);
				$rootScope.position = position;
				Map.broadcast.center();
			}
		},
		position : {	//Raw data for position
		},
		broadcast : {
			reset : function() {
				$rootScope.$broadcast('ui.map.reset');
			},
			location : function() {
				$rootScope.$broadcast('ui.map.location');
			},
			center : function () {	//Broadcast map center event
				$rootScope.$broadcast('ui.map.marker.center');
			},
			marker : {
				add : function() {	//PReviously broadcastNewMapMarker
					$rootScope.$broadcast('ui.map.marker.new');
				}
			}
		},
		error : function() {
			console.log(map.position);
		},
		marker : {
			center : function(data) {	//Center map on {lat: data.lat, lng: data.lon} through broadcast
				$rootScope.mapCenter = {lat: data.lat, lng: data.lng};
				Map.broadcast.center();
			},
			add : function (data, title, id) {
				if(data[0])	{	//If we actually got data
					$rootScope.map.markers = {lat: data[0].lat, lng: data[0].lon, title: title, id:id};
					Map.ui.broadcast.marker.add();
				}
			},
			query : function(item, title, id) {	//Query API for lon and lat
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
							Map.marked.add (data, title, id);
						} else {
							$http.get('http://nominatim.openstreetmap.org/search/?format=json&postalcode='+address.postalCode+'&country='+address.countryCode+'').success(function(data) {
								if(data[0])	{
									Map.marked.add (data, title, id);
								}
							});
						}
						
					});
				}
			},
			temp : {}
		}
	}
	
	//Get location of users
	Map.location.get();
	
	return Map;
}); 