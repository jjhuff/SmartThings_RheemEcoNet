/**
 *  Rheem Econet Water Heater
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
 *  Last updated : 7/15/2015
 */
metadata {
	definition (name: "Rheem Econet Water Heater", namespace: "copy-ninja", author: "Jason Mok") {
		capability "Actuator"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Thermostat Heating Setpoint"
		
		command "heatLevelUp"
		command "heatLevelDown"
		command "updateDeviceData", ["string"]
	}

	simulator { }

	tiles {
		valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, width: 2, height: 2) {
			state("heatingSetpoint", label:'${currentValue}°',
				backgroundColors:[
					[value: 90,  color: "#f49b88"],
					[value: 100, color: "#f28770"],
					[value: 110, color: "#f07358"],
					[value: 120, color: "#ee5f40"],
					[value: 130, color: "#ec4b28"],
					[value: 140, color: "#ea3811"]					
				]
			)
		}
		standardTile("heatLevelUp", "device.switch", canChangeIcon: false, inactiveLabel: true, decoration: "flat" ) {
			state("heatLevelUp",   action:"heatLevelUp",   icon:"st.thermostat.thermostat-up", backgroundColor:"#F7C4BA")
		}  
		standardTile("heatLevelDown", "device.switch", canChangeIcon: false, inactiveLabel: true, decoration: "flat") {
			state("heatLevelDown", action:"heatLevelDown", icon:"st.thermostat.thermostat-down", backgroundColor:"#F7C4BA")
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state("default", action:"refresh.refresh",        icon:"st.secondary.refresh")
		}
		main "heatingSetpoint"
		details(["heatingSetpoint", "heatLevelUp", "heatLevelDown", "refresh"])
	}
}

def parse(String description) { }

def poll() { updateDeviceData(parent.getDeviceData(this.device)) }

def refresh() { parent.refresh() }

def setHeatingSetpoint(Number heatingSetPoint) {
	def actualData = parent.getDeviceData(this.device).clone()
    def deviceData = convertTemperatureUnit(actualData.clone(), getTemperatureScale())
    
	heatingSetPoint = (heatingSetPoint < deviceData.minTemp)? deviceData.minTemp : heatingSetPoint
	heatingSetPoint = (heatingSetPoint > deviceData.maxTemp)? deviceData.maxTemp : heatingSetPoint
	deviceData.setPoint = heatingSetPoint
    
	updateDeviceData(deviceData)    
	
	state.deviceData = convertTemperatureUnit(deviceData, actualData.temperatureUnit)
	runIn(5, setDeviceSetPoint, [overwrite: true])
}

def heatLevelUp() { 
	def actualData = parent.getDeviceData(this.device).clone()
	def deviceData = convertTemperatureUnit(actualData.clone(), getTemperatureScale())
	def heatingSetPoint = device.currentValue("heatingSetpoint")
	if (actualData.temperatureUnit != getTemperatureScale()) {
		if (actualData.temperatureUnit == "C") {
			actualData.setPoint = Math.round(fahrenheitToCelsius(heatingSetPoint)).toInteger()
			actualData.setPoint = ((actualData.setPoint + 1) > actualData.maxTemp)? actualData.maxTemp : (actualData.setPoint + 1)
			heatingSetPoint = Math.round(celsiusToFahrenheit(actualData.setPoint)).toInteger()
		} else {
			heatingSetPoint = ((heatingSetPoint + 1) > deviceData.maxTemp)? deviceData.maxTemp : (heatingSetPoint + 1)
			actualData.setPoint = Math.round(celsiusToFahrenheit(heatingSetPoint)).toInteger()
		}
	} else {
		heatingSetPoint = ((heatingSetPoint + 1) > deviceData.maxTemp)? deviceData.maxTemp : (heatingSetPoint + 1)
		actualData.setPoint = heatingSetPoint
	}
	deviceData.setPoint = heatingSetPoint
	
	updateDeviceData(deviceData) 
	setHeatingSetpoint(heatingSetPoint) 
}	

def heatLevelDown() { 
	def actualData = parent.getDeviceData(this.device).clone()
	def deviceData = convertTemperatureUnit(actualData.clone(), getTemperatureScale())
	def heatingSetPoint = device.currentValue("heatingSetpoint")
	if (actualData.temperatureUnit != getTemperatureScale()) {
		if (actualData.temperatureUnit == "C") {
			actualData.setPoint = Math.round(fahrenheitToCelsius(heatingSetPoint)).toInteger()
			actualData.setPoint = ((actualData.setPoint - 1) < actualData.minTemp)? actualData.minTemp : (actualData.setPoint - 1)
			heatingSetPoint = Math.round(celsiusToFahrenheit(actualData.setPoint)).toInteger()
		} else {
			heatingSetPoint = ((heatingSetPoint - 1) < deviceData.minTemp)? deviceData.minTemp : (heatingSetPoint - 1)
			actualData.setPoint = Math.round(celsiusToFahrenheit(heatingSetPoint)).toInteger()
		}
	} else {
		heatingSetPoint = ((heatingSetPoint - 1) < deviceData.minTemp)? deviceData.minTemp : (heatingSetPoint - 1)
		actualData.setPoint = heatingSetPoint
	}
	deviceData.setPoint = heatingSetPoint
	
	updateDeviceData(deviceData) 
	setHeatingSetpoint(heatingSetPoint) 
}

def updateDeviceData(actualData) {
	def deviceData = convertTemperatureUnit(actualData, getTemperatureScale())
	sendEvent(name: "heatingSetpoint", value: deviceData.setPoint, unit: getTemperatureScale())
}

def convertTemperatureUnit(actualData = [], temperatureUnit) {
	def deviceData = actualData.clone()
	if (deviceData.temperatureUnit != temperatureUnit) { 
		if (deviceData.temperatureUnit == "F") {
			deviceData.temperatureUnit = "C"
			deviceData.setPoint = Math.round(fahrenheitToCelsius(deviceData.setPoint)).toInteger() as Integer
			deviceData.maxTemp = Math.round(fahrenheitToCelsius(deviceData.maxTemp)).toInteger() as Integer
			deviceData.minTemp = Math.round(fahrenheitToCelsius(deviceData.minTemp)).toInteger() as Integer
		} else {
			deviceData.temperatureUnit = "F"
			deviceData.setPoint = Math.round(celsiusToFahrenheit(deviceData.setPoint)).toInteger() as Integer
			deviceData.maxTemp = Math.round(celsiusToFahrenheit(deviceData.maxTemp)).toInteger() as Integer
			deviceData.minTemp = Math.round(celsiusToFahrenheit(deviceData.minTemp)).toInteger() as Integer
		}
	}
	return deviceData
}

def setDeviceSetPoint() { parent.setDeviceSetPoint(this.device, state.deviceData) }