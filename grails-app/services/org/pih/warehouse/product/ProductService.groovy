package org.pih.warehouse.product

import java.io.ExpiringCache.Entry;

import groovy.xml.Namespace;

import org.pih.warehouse.importer.ImportDataCommand;

class ProductService {
	
	def grailsApplication
	
	
	/**
	 * Examples 
	 * 
	 * RxNorm
	 * 
	 * UPC Database
	 * http://www.upcdatabase.com/item/048001006812
	 * 
	 * Search UPC
	 * http://www.searchupc.com/default.aspx?q=048001006812
	 * 
	 * Google Product Search
	 * https://www.googleapis.com/shopping/search/v1/public/products?key=AIzaSyCAEGyY6QpPbm3DiHmtx6qIZ_P40FnF3vk&country=US&q=${q}&alt=atom&crowdBy=brand:1
	 * 
	 * @param q
	 * @return
	 */
	List<Product> findProducts(String q) { 
		def urlString = "https://www.googleapis.com/shopping/search/v1/public/products?key=AIzaSyCAEGyY6QpPbm3DiHmtx6qIZ_P40FnF3vk&country=US&q=${q}&alt=atom&crowdBy=brand:1";
		def url = new URL(urlString)
		def connection = url.openConnection()

		def products = new ArrayList();
		if(connection.responseCode == 200){
			def xml = connection.content.text			
			//  <feed gd:kind="shopping#products" 
			// gd:etag="&quot;s_TKVMJ0f6e67wg989LFuFzazq0/0m3WlwDtAy5plGxzl-bgZJM-ufI&quot;" 
			// xmlns="http://www.w3.org/2005/Atom" 
			// xmlns:gd="http://schemas.google.com/g/2005" 
			// xmlns:openSearch="http://a9.com/-/spec/opensearchrss/1.0/" 
			// xmlns:s="http://www.google.com/shopping/api/schemas/2010">

			//def root = new XmlSlurper().parseText(blog).declareNamespace(dc: "http://purl.org/dc/elements/1.1/");
			//root.channel.item.findAll { item ->
			//	d.any{entry -> item."dc:date".text() =~ entry.key} && a.any{entry -> item.tags.text() =~ entry
			//}

			def root = new XmlParser(false, true).parseText(xml)
			def ns = new Namespace("http://www.google.com/shopping/api/schemas/2010", "s")
			
			root.entry.each { entry ->
				def product = new Product()
				def productUrl = entry[ns.product][ns.link].text()
				product.name = "<a href='${productUrl}'>" + entry.title.text() + "</a>"
				product.category = getRootCategory();
				product.description = entry[ns.product][ns.description].text()
				product.manufacturer = entry[ns.product][ns.brand].text()
				product.upc = entry[ns.product][ns.gtin].text()
				// HACK iterates over all images, but only keeps the last one
				// Need to add these to product->documents 
				//def imageLinks = ""
				entry[ns.product][ns.images][ns.image].each { image ->
					product.productCode = image.@'link'
				}
				
				products << product
			}

			/*
			def root = new XmlSlurper().parseText(xml).
				declareNamespace(s: "http://www.google.com/shopping/api/schemas/2010")
			
			root.entry.each { entry ->
				def product = new Product()
				product.name = entry.title
				println " * " + product.name
				product.description = entry."s:product"."s:description".text()
				def link = entry."s:product"."s:link".text()
				product.description += "<br/><a href='" + link + "'>click here</a>" 
				product.manufacturer= entry."s:product"."s:brand".text()
				product.upc = entry."s:product"."s:gtin".text()
				product.manufacturer += " (" + entry."s:product"."s:author"."s:name".text() + ")"
				//println "\timages -> " + entry["s:product"]["s:images"]
				//entry["s:product"]["s:images"].each { image ->
				//	println "\timage -> " + image
				//}
				
				
				product.category = getRootCategory();
				products << product
				//result.name = geonames.geoname.name as String
				//result.lat = geonames.geoname.lat as String
				//result.lng = geonames.geoname.lng as String
				//result.state = geonames.geoname.adminCode1 as String
				//result.country = geonames.geoname.countryCode as String
			}
			*/
			
		}
		else{
			log.error(url)
			log.error(connection.responseCode)
			log.error(connection.responseMessage)
			throw new Exception(connection.responseMessage)
		}		
		return products
		
	}
	
	
	List<Product> getProducts(String [] ids) { 
		def products = Product.createCriteria().list() {
			'in'("id", ids)
		}
		return products
	}
		
	
	Category getRootCategory() {
		def rootCategory;
		def categories = Category.findAllByParentCategoryIsNull();
		if (categories && categories.size() == 1) {
			rootCategory = categories.get(0);
		}
		else {
			rootCategory = new Category();
			rootCategory.categories = [];
			categories.each {
				rootCategory.categories << it;
			}
		}
		return rootCategory;
	}
	
	List getCategoryTree() { 
		return Category.list();		
	}
	
	List getQuickCategories() {
		List quickCategories = new ArrayList();
		String quickCategoryConfig = grailsApplication.config.inventoryBrowser.quickCategories;
		
		Category.findAll().each {
			if (it.parentCategory == null && !quickCategories.contains(it)) {
				quickCategories.add(it);
			}
		}

		if (quickCategoryConfig) {
			quickCategoryConfig.split(",").each {
				Category c = Category.findByName(it);
				if (c != null) {
					quickCategories.add(c);
				}
			};
		}
		return quickCategories;
	}
	
	
	public void validateData(ImportDataCommand command) { 
		log.info "validate data test "
		// Iterate over each row and validate values
		command?.data?.each { Map params ->
			//log.debug "Inventory item " + importParams
			log.info "validate data " + params
			//command?.data[0].newField = 'new field'
			//command?.data[0].newDate = new Date()
			params.prompts = [:]
			params.prompts["product.id"] = Product.findAllByNameLike("%" + params.search1 + "%")  
			
			//def lotNumber = (params.lotNumber) ? String.valueOf(params.lotNumber) : null;
			//if (params?.lotNumber instanceof Double) {
			//	errors.reject("Property 'Serial Number / Lot Number' with value '${lotNumber}' should be not formatted as a Double value");
			//}
			//else if (!params?.lotNumber instanceof String) {
			//	errors.reject("Property 'Serial Number / Lot Number' with value '${lotNumber}' should be formatted as a Text value");
			//}
	
	
		}

	}
	
	public void importData(ImportDataCommand command) {
		log.info "import data"

		try {
			// Iterate over each row
			command?.data?.each { Map params ->

				log.info "import data " + params
				
				/*
				// Create product if not exists
				Product product = Product.findByName(params.productDescription);
				if (!product) {
					product = new Product(params)
					product.name = params.productDescription
					//upc:params.upc,
					//ndc:params.ndc,
					//category:category,
					//manufacturer:manufacturer,
					//manufacturerCode:manufacturerCode,
					//unitOfMeasure:unitOfMeasure);

					if (!product.save()) {
						command.errors.reject("Error saving product " + product?.name)
					}
					//log.debug "Created new product " + product.name;
				}
				*/
			}

		} catch (Exception e) {
			log.error("Error importing data ", e);
			throw e;
		}

	}
	
}
