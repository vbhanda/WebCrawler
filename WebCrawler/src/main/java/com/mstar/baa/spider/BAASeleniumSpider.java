package com.mstar.baa.spider;

import static com.mstar.baa.utilities.BAASpiderUtility.nullOrZero;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * 
 * @author Sameer Gaware
 *
 */
public abstract class BAASeleniumSpider extends BAABaseSpider {

	private String chromeDriverPath = null;  
	private WebDriver driver = null;
	private WebDriver webDrivers[] = new WebDriver[10]; 
	private Drivers currentDriver = null;
	private BlockingQueue<String> linksToVisit = null;
	private String mainPageBody = null;
	List<String> allLinks = null;
	private int threadCount = 0;
	private ChromeOptions options = null;

	public BAASeleniumSpider() {
		currentDriver = Drivers.HEADLESSCHROME;
	}

	public void setDriver(Drivers currentDriver)
	{
		this.currentDriver = currentDriver;
	}

	@Override
	public String getBody(String url) {
		driver.get(startLink);

		String body = driver.getPageSource();
		mainPageBody = body;
		return body;
	}

	@Override
	public void preparePhase()  {

		try {

			linksToVisit = new ArrayBlockingQueue<>(1000);
			linksToVisit.add(startLink); 
			allLinks = new CopyOnWriteArrayList<>();

			System.out.println("Inside Headless chrome");

			chromeDriverPath = "E:\\Chrome Driver\\chromedriver.exe" ;  
			System.setProperty("webdriver.chrome.driver", chromeDriverPath);  
			options = new ChromeOptions();
			options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200","--ignore-certificate-errors");

			driver = new ChromeDriver(options);

		}	catch(Exception  e) {

		}
	}


	@Override
	public void postPhase(String url, String body) {
		// TODO Auto-generated method stub

	}


	@Override
	public List<String> extractURLPhase(String url, String body) {
		// TODO Auto-generated method stub
		return extractURLPhase(url, body,null);
	}


	public List<String> extractURLPhase(String url, String body, WebDriver webDrivers2) {

		List<String> links = new ArrayList<>();

		if(webDrivers2 == null)
			return links;

		if(skipExtractLinkPhase)
			return links; 

		if(!setExtractLinkFromDataPage && isDataPage(url))
			return links;

		webDrivers2.get(url); 
		System.out.println("Extracting link from : "+url); 

		List<WebElement> allElements = webDrivers2.findElements(By.tagName("a"));
		if(!nullOrZero(allElements)) {
			for(WebElement we : allElements) {
				String link = we.getAttribute("href");
				link = normalizeURL(link);

				if(nullOrZero(link))
					continue;
				if((isDataPage(link) || isTraversalPage(link)) && !linkToCache.containsKey(link)) {
					links.add(link);
					linkToCache.put(link, "");
					System.out.println("	"+link); 
				}
			}
		}
		return links;
	}

	private void extractURLPhase() throws InterruptedException {
		for(int index = 0 ; index < threadCount ; index++) {
			webDrivers[index] = new ChromeDriver(options);
			WebDriver wdr = webDrivers[index]; 

			executorService.submit(() -> {
				while(!linksToVisit.isEmpty()) {
					String link = linksToVisit.peek();

					List<String> links = extractURLPhase(link, null, wdr);
					if(!nullOrZero(links)) {
						List<String> traversalLinks = links.stream().filter(element -> isTraversalPage(element)).collect(Collectors.toList());
						List<String> dataLinks = links.stream().filter(element -> isTraversalPage(element)).collect(Collectors.toList());
						dataLinks.stream().forEach(element -> linkToCache.put(element, ""));
						linksToVisit.addAll(traversalLinks);
					}
					System.out.println("Removing "+link+" from queue "+linksToVisit.size()+" "); 
					linksToVisit.poll();
				}
				wdr.close();
			});
		}
		executorService.shutdown();
		executorService.awaitTermination(1,TimeUnit.DAYS); 
		System.out.println("Shutting down executor service Caught "+linkToCache.size()+" data pages"); 
	}

	@Override
	public void start(String startLink, int threadCount) {

		this.startLink = startLink;
		this.threadCount = threadCount;
		executorService = Executors.newFixedThreadPool(threadCount);

		switch (currentDriver) {
		case HEADLESSCHROME: {

			//			driver.quit();
			try
			{
				preparePhase();
				extractURLPhase();
				mainPhase();

				System.out.println("Finished Extract link phase"); 
			}catch (Exception e) {
				e.printStackTrace();
			}
		}

		break;
		case CHROME: {

		}

		break;
		}

	}

	private void mainPhase() {



		System.out.println("Inside main Phase");  
	}

}
