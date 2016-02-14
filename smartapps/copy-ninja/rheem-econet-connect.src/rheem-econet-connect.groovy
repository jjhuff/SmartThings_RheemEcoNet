/**
 *  Rheem EcoNet (Connect)
 *
 *  Copyright 2015 Jason Mok
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Last Updated : 7/15/2015
 *
 */
definition(
    name: "Rheem EcoNet (Connect)",
    namespace: "copy-ninja",
    author: "Jason Mok",
    description: "Connect to Rheem EcoNet",
    category: "SmartThings Labs",
    iconUrl: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@1x.png",
    iconX2Url: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@2x.png",
    iconX3Url: "http://smartthings.copyninja.net/icons/Rheem_EcoNet@3x.png")


preferences {
	page(name: "prefLogIn", title: "Rheem EcoNet")    
	page(name: "prefListDevice", title: "Rheem EcoNet")
}

/* Preferences */
def prefLogIn() {
	def showUninstall = username != null && password != null 
	return dynamicPage(name: "prefLogIn", title: "Connect to Rheem EcoNet", nextPage:"prefListDevice", uninstall:showUninstall, install: false) {
		section("Login Credentials"){
			input("username", "email", title: "Username", description: "Rheem EcoNet Email")
			input("password", "password", title: "Password", description: "Rheem EcoNet password (case sensitive)")
		} 
		section("Advanced Options"){
			input(name: "polling", title: "Server Polling (in Minutes)", type: "int", description: "in minutes", defaultValue: "5" )
			paragraph "This option enables author to troubleshoot if you have problem adding devices. It allows the app to send information exchanged with Rheem EcoNet server to the author. DO NOT ENABLE unless you have contacted author at jason@copyninja.net"
			input(name:"troubleshoot", title: "Troubleshoot", type: "boolean")
		}
	}
}

def prefListDevice() {	
	if (login()) {
		def waterHeaterList = getWaterHeaterList()
		if (waterHeaterList) {
			return dynamicPage(name: "prefListDevice",  title: "Devices", install:true, uninstall:true) {
				section("Select which water heater to use"){
					input(name: "waterheater", type: "enum", required:false, multiple:true, metadata:[values:waterHeaterList])
				}
			}
		} else {
			return dynamicPage(name: "prefListDevice",  title: "Error!", install:false, uninstall:true) {
				section(""){ paragraph "Could not find any devices"  }
			}
		}
	} else {
		return dynamicPage(name: "prefListDevice",  title: "Error!", install:false, uninstall:true) {
			section(""){ paragraph "The username or password you entered is incorrect. Try again. " }
		}  
	}
}


/* Initialization */
def installed() { initialize() }
def updated() { 
	unsubscribe()
	initialize() 
}
def uninstalled() {
	unschedule()
    unsubscribe()
	getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}	

def initialize() {
	// Set initial states
	state.polling = [ last: 0, rescheduler: now() ]  
	state.troubleshoot = null
	state.data = [:]
    state.setData = [:]

	// Create selected devices
	def waterHeaterList = getWaterHeaterList()
	def selectedDevices = [] + getSelectedDevices("waterheater")
	selectedDevices.each { (getChildDevice(it))?:addChildDevice("copy-ninja", "Rheem Econet Water Heater", it, null, ["name": "Rheem Econet: " + waterHeaterList[it]]) }
    
	// Remove unselected devices
	def deleteDevices = (selectedDevices) ? (getChildDevices().findAll { !selectedDevices.contains(it.deviceNetworkId) }) : getAllChildDevices()
	deleteDevices.each { deleteChildDevice(it.deviceNetworkId) } 
	
	//Subscribes to sunrise and sunset event to trigger refreshes
	subscribe(location, "sunrise", runRefresh)
	subscribe(location, "sunset", runRefresh)
	subscribe(location, "mode", runRefresh)
	subscribe(location, "sunriseTime", runRefresh)
	subscribe(location, "sunsetTime", runRefresh)
	    
	//Refresh devices
	runRefresh()
	
}

def getSelectedDevices( settingsName ) {
	def selectedDevices = []
	(!settings.get(settingsName))?:((settings.get(settingsName)?.getAt(0)?.size() > 1)  ? settings.get(settingsName)?.each { selectedDevices.add(it) } : selectedDevices.add(settings.get(settingsName)))
	return selectedDevices
}


/* Data Management */
// Listing all the water heaters you have in Rheem EcoNet
private getWaterHeaterList() { 	 
	def deviceList = [:]
	apiGet("/v3/eco/myequipmentcheck", [] ) { response ->
    	if (response.status == 200) {
        	response.data.Equipment.each { device ->
            	device.WH.each { 
                	def dni = [ app.id, "WaterHeater", it.EquipmentId ].join('|')
                	log.debug "WaterHeater ${dni}: ${it.SetPoint}"
                    state.data?.put(dni,[ 
						minTemp: it.MinTemp,
						maxTemp: it.MaxTemp,
						modesAvailable: it.ModesAvailable,
						mode: it.Mode,
						modeDisplay: it.ModeDisplay,
						setPoint: it.SetPoint,
						hotWaterAvailability: it.HotWaterAvailability,
						hotWaterRecoveryMin: it.HotWaterRecoveryMin,
						temperatureUnit: it.TemperatureDisplayMode
                    ])
                    deviceList.put(dni,it.SystemName)
                }
            }
        }
    }
    return deviceList
}

// Refresh data
def refresh() {		
	if (updateData()) { 
		log.info "Refreshing data..."
        // update last refresh
		state.polling?.last = now()

		// get all the children and send updates
		getAllChildDevices().each { it.updateDeviceData(state.data?.get(it.deviceNetworkId)) }
	}    
    
	//schedule the rescheduler to schedule refresh ;)
	if ((state.polling?.rescheduler?:0) + 2400000 < now()) {
		log.info "Scheduling Auto Rescheduler.."
		runEvery30Minutes(runRefresh)
		state.polling?.rescheduler = now()
	}
}

// Updates data for devices
private updateData() { return (login()) ? ((getWaterHeaterList())? true : false) : false }

// Schedule refresh
def runRefresh(evt) {
	log.info "Last refresh was "  + ((now() - state.polling?.last?:0)/60000) + " minutes ago"
	// Reschedule if  didn't update for more than 5 minutes plus specified polling
	if ((((state.polling?.last?:0) + (((settings.polling?.toInteger()?:1>0)?:1) * 60000) + 300000) < now()) && canSchedule()) {
		log.info "Scheduling Auto Refresh.."
		schedule("* */" + ((settings.polling?.toInteger()?:1>0)?:1) + " * * * ?", refresh)
	}
    
	// Force Refresh NOWWW!!!!
	refresh()
    
	//Update rescheduler's last run
	if (!evt) state.polling?.rescheduler = now()
}
// Get single device status
def getDeviceData(childDevice) { return state.data?.get(childDevice.deviceNetworkId) }
def getDeviceData(childDevice, dataName) { return state.data?.get(childDevice.deviceNetworkId)?.get(dataName) }
def setDeviceSetPoint(childDevice, deviceData = []) { 
	state.data = deviceData.clone()
	if (login()) {
    	apiPost("/v3/eco/myequipmentattributes/savewhtemp", [
        	body: [
            	TempDisplayMode: deviceData.temperatureUnit,
                EquipmentId: getDeviceID(childDevice),
                SetPoint: deviceData.setPoint
            ]
        ])
    }
}
def setDeviceSetPointDelay(setData) {
	
}
def getDeviceID(childDevice) { return childDevice.deviceNetworkId.split("\\|")[2] }

/* Access Management */
private login() { 
    if (state.session?.expiration < now()) {
    	def apiPath = (state.session?.refreshToken) ? "/v1/public/tokens/refresh" : "/v1/eco/authenticate"
    	apiGet(apiPath, [] ) { response ->
            if (response.status == 200) {
                state.session = [ 
                    accessToken: response.data.AccessToken,
                    tokenType: response.data.TokenType,
                    refreshToken: response.data.RefreshToken,
                    expiration: now() + 150000
                ]
                return true
            } else {
                return false
            }
		} 
    } else { 
		return true
	}
}

/* API Management */
// HTTP GET call
private apiGet(apiPath, apiParams = [], callback = {}) {	
	// set up parameters
	apiParams = [ 
		uri: getApiURL(),
		path: apiPath,
        headers: ["Authorization": getApiAuth(), "X-ClientID": getApiClientID(), "X-Timestamp":now()],
        requestContentType: "application/json",
	] + apiParams
	
	try {
		httpGet(apiParams) { response -> 
        	callback(response)
        }
	}	catch (Error e)	{
		log.debug "API Error: $e"
	}
}

// HTTP POST call
private apiPost(apiPath, apiParams = [], callback = {}) {	
	// set up parameters
	apiParams = [ 
		uri: getApiURL(),
		path: apiPath,
        headers: ["Authorization": getApiAuth(), "X-ClientID": getApiClientID(), "X-Timestamp":now()],
        requestContentType: "application/json",
	] + apiParams
	
	try {
		httpPost(apiParams) { response -> 
        	log.debug("response: ${response.data}")
        	callback(response)
        }
	}	catch (Error e)	{
		log.debug "API Error: $e"
	}
}

private getApiClientID() { return "4890422047775.apps.rheemapi.com" }
private getApiURL() { 
	def troubleshoot = "false"
	if (settings.troubleshoot == "true") {
		if (!(state.troubleshoot)) state.troubleshoot = now() + 3600000 
		troubleshoot = (state.troubleshoot > now()) ? "true" : "false"
	}
	return (troubleshoot == "true") ? "https://io-myrheem-com-uyg33xguwugq.runscope.net" : "https://io.myrheem.com" }
private getApiAuth() {
	if (!((state.session?.refreshToken)||(state.session?.refreshToken=="INVALID TOKEN"))) {
		def basicAuth = settings.username + ":" + settings.password
		return "Basic " + basicAuth.encodeAsBase64()
	} else if (state.session?.expiration < now()) {
		return "Refresh: " + state.session?.refreshToken
	} else {
		return state.session?.tokenType + ": " + state.session?.accessToken
	}
}