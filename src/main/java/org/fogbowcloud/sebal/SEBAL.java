package org.fogbowcloud.sebal;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fogbowcloud.sebal.model.image.HOutput;
import org.fogbowcloud.sebal.model.image.Image;
import org.fogbowcloud.sebal.model.image.ImagePixel;
import org.fogbowcloud.sebal.model.image.ImagePixelOutput;
import org.fogbowcloud.sebal.model.satellite.Satellite;
import org.fogbowcloud.sebal.parsers.EarthSunDistance;
import org.fogbowcloud.sebal.parsers.JSonParser;
import org.json.JSONObject;

public class SEBAL {

	private EarthSunDistance earthSunDistance;

	public SEBAL() throws Exception {
		earthSunDistance = new EarthSunDistance();
	}

	double deltaE(double Rn, double H, double G) {
		return Rn - H - G;
	}

	double Rn(double alpha, double RSDown, double RLDown, 
			double RLUp, double epsilonZero) {
		return (1.0 - alpha) * RSDown + RLDown - RLUp - (1 - epsilonZero) * RLDown;
	}

	double LLambda(double LLambdaMin, double LLambdaMax, double DN) {
		return LLambdaMin + ((LLambdaMax - LLambdaMin) / 255.0) * DN;
	}

	double rho(double LLambda, double d, double ESUN, double cosTheta) {
		return (Math.PI * LLambda * Math.pow(d, 2)) / (ESUN * cosTheta);
	}

	double alphaToa(double rho1, double rho2, double rho3, double rho4, 
			double rho5, double rho7) {
		return 0.298221 * rho1 + 0.270098 * rho2 + 0.230997 * rho3 + 0.155051 * rho4 
				+ 0.033085 * rho5 + 0.012548 * rho7;
	}

	double tauSW(double z) {
		return 0.75 + 2 * 0.00001 * z;
	}

	double alpha(double alphaToa, double tauSW) {
		double alphaP = 0.03;
		return (alphaToa - alphaP) / Math.pow(tauSW, 2);
	}

	double RSDown(double cosTheta, double d, double tauSW) {
		double GSC = 1367;
		return (GSC * cosTheta * tauSW) / Math.pow(d, 2);
	}

	double NDVI(double rho3, double rho4) {
		return (rho4 - rho3) / (rho4 + rho3);
	}

	double SAVI(double rho3, double rho4) {
		double L = 0.1;
		return (1 + L) * (rho4 - rho3) / (L + rho4 + rho3);
	}

	double EVI(double rho1, double rho3, double rho4) {
		double G = 2.5;
		double C1 = 6;
		double C2 = 7.5;
		double L = 1;
		return G * ((rho4 - rho3) / (rho4 + C1 * rho3 - C2 * rho1 + L));
	}

	double IAF(double SAVI) {
		if (SAVI == 0.69) {
			return 6.;
		}
		if (SAVI < 0.1) {
			return 0;
		}
		return -1 * (Math.log((0.69 - SAVI) / 0.59) / 0.91); 
	}

	double epsilonNB(double IAF) {
		if (IAF >= 3.) {
			return 0.98;
		}
		return 0.97 + 0.0033 * IAF;
	}

	double epsilonZero(double IAF) {
		if (IAF >= 3.) {
			return 0.98;
		}
		return 0.95 + 0.01 * IAF;
	}

	double TS(double K2, double epsilonNB, double K1, double LLambda6) {
		return K2 / Math.log((epsilonNB * K1 / LLambda6) + 1);
	}

	static final double sigma = 5.67 * Math.pow(10, -8);

	double RLUp(double epsilonZero, double TS) {
		return epsilonZero * sigma * Math.pow(TS, 4);
	}

	double RLDown(double epsilonA, double TA) {
		return epsilonA * sigma * Math.pow(TA + 273.15, 4);
	}

	double epsilonA(double tauSW) {
		return 0.85 * (Math.pow(-1 * Math.log(tauSW), 0.09));
	}

	double G(double TS, double alpha, double NDVI, double Rn) {
		if (NDVI < 0) {
			return Rn * 0.5;
		}
		return ((TS - 273.15)/alpha * (0.0038 * alpha + 0.0074 * Math.pow(alpha, 2)) * (1 - 0.98 * Math.pow(NDVI, 4))) * Rn;
		//		return Math.abs((TS - 273.15)*(0.0038 + 0.0074 * alpha)*(1-0.98*Math.pow(NDVI, 4))) * Rn;
	}

	static final double rho = 1.15;
	static final double cp = 1004;

	double H(double a, double b, double Ts, double rah) {
		double H = (rho * cp) * (a + b * Ts) / rah;
		return H;
	}

	double HQuente(double Rn, double G) {
		return Rn - G;
	}

	static final double k = 0.41;

	double uAsterisk(double ux, double zx, double d, double z0m) {
		return k * ux / Math.log((zx - d)/ z0m);
	}

	double u200(double uAsterisk, double d, double z0m) {
		return (uAsterisk * Math.log((200 - d) / z0m)) / k;
	}

	double uAsteriskxy(double u200, double dxy, double z0mxy) {
		return (k * u200) / Math.log((200 - dxy) / z0mxy);
	}

	double rahxy(double uAsteriskxy) {
		double z1 = 0.1;
		double z2 = 2.0;
		return Math.log(z2 / z1) / (uAsteriskxy * k);
	}

	double b(double Tquente, double Tfrio, double rahquente, double Rnquente, double Gquente) {
		return ((rahquente * (Rnquente - Gquente)) / rho * cp) / (Tquente - Tfrio);
	}

	double a(double b, double Tfrio) {
		return -1 * b * Tfrio;
	}

	static final double g = 9.81;

	double Lxy(double uAsteriskxy, double TSxy, double Hxy) {
		double L = -1 * (rho * cp * Math.pow(uAsteriskxy, 3) * TSxy) / (k * g * Hxy);
		return L;
	}

	double uAsteriskCorrxy(double u200, double dxy, double z0mxy, double psimxy) {
		return (k * u200) / (Math.log((200 - dxy) / z0mxy) - psimxy);
	}

	double rahCorrxy(double psih1xy, double psih2xy, double uAsteriskxy) {
		double z1 = 0.1;
		double z2 = 2.;
		return (Math.log(z2 / z1) - psih2xy + psih1xy) / (uAsteriskxy * k);
	}

	double ET24h(double lambda24h, double Rn24h) {
		return lambda24h * Rn24h;
	}

	double lambdaInst(double lambda, double ET, double Rn, double G) {
		return (lambda * ET) / (Rn - G);
	}

	double Rn24h(double alpha, double RSDown, double tauW24h) {
		return (1 - alpha) * RSDown - 100 * tauW24h;
	}

	double z0mxy(double SAVI) {
		//TODO Parametrizar os variaveis obtidas de forma empirica
		double z0mx = -5.809;
		double z0my = 5.62;
		return Math.exp(z0mx + SAVI * z0my);
	}

	double z0m(double h) {
		//TODO Parametrizar os variaveis obtidas de forma empirica
		return h * 0.12;
	}

	double hc(double z0m) {
		return z0m / 0.136;
	}

	double d0(double hc) {
		return 2 * hc / 3;
	}

	double dTQuente(double rahquente, double Rnquente, double Gquente) {
		return (rahquente * (Rnquente - Gquente)) / (rho * cp);
	}

	double psiH1(double L) {
		double psiH1 = 0;
		if (L == 0) {
			psiH1 = 0;
		}
		else if(L > 0) {
			psiH1 = -5*(0.1/L);
		}
		else {
			double y = Math.pow((1 - (16 * (0.1 / L))), 0.25);
			psiH1 = 2 * Math.log((1 + Math.pow(y, 2)) / 2);
		}
		return psiH1;
	}

	double psiH2(double L) {
		double psiH2 = 0;
		if (L == 0) {
			psiH2 = 0;
		}
		else if(L > 0) {
			psiH2 = -5*(2./L);
		}
		else {
			double y = Math.pow((1 - (16 * (2 / L))), 0.25);
			psiH2 = 2 * Math.log((1 + Math.pow(y, 2)) / 2);
		}
		return psiH2;
	}

	double psim(double L) {
		double psim = 0;
		if (L > 0) {
			psim = -5*(200/L);
		}
		else if (L == 0) {
			psim = 0;
		}
		else {
			double y = Math.pow((1 - (16 * (200 / L))), 0.25);
			psim = 2 * Math.log((1 + y) / 2) + Math.log((1 + Math.pow(y, 2)) / 2) - 2 * Math.atan(y) + 0.5 * Math.PI;
		}
		return psim;
	}

	public void run(Satellite satellite, Image image, String fileName) {
		//Start Process Pixel Thread
		for (ImagePixel imagePixel : image.pixels()) {
			ImagePixelOutput output = processPixel(satellite, imagePixel);
			imagePixel.setOutput(output);
		}
		image.choosePixelsQuenteFrio();

		ImagePixel pixelQuente = image.pixelQuente();
		ImagePixelOutput pixelQuenteOutput = pixelQuente.output();

		ImagePixel pixelFrio = image.pixelFrio();
		ImagePixelOutput pixelFrioOutput = pixelFrio.output();
		try {
			Socket socket = new Socket("localhost",1234);
//			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
			JSONObject pixelQuenteJSon = JSonParser.parseDefaultImagePixelToJson(pixelQuente); 	
			OutputStreamWriter out = new OutputStreamWriter(
					socket.getOutputStream(), StandardCharsets.UTF_8);
			out.write(pixelQuenteJSon.toString());
			out.flush();
			out.close();
			socket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}


		// TODO Escolhendo a weather station mais proxima ao pixelQuente
		// TODO A altura da vegetacao deve ser parametrizada
		double z0m = z0m(pixelQuente.hc());

		double uAsterisk = uAsterisk(pixelQuente.ux(), 
				pixelQuente.zx(), pixelQuente.d(), z0m);

		double u200 = u200(uAsterisk, pixelQuente.d(), z0m);
		double d0 = pixelQuente.d();

		double Hcal = HQuente(pixelQuenteOutput.Rn(), pixelQuenteOutput.G());
		double z0mxy = z0mxy(pixelQuenteOutput.SAVI());
		double uAsteriskxy = uAsteriskxy(u200, d0, z0mxy);
		double rahxy = rahxy(uAsteriskxy);

		double uAsteriskCorrxy = uAsteriskxy;
		double rahxyCorr = Double.MAX_VALUE;

		//		int i = 1;
		//TODO configuracao
		//		double H = Hcal;

		List<HOutput> hOutput = hOutput(rahxyCorr, rahxy, uAsteriskCorrxy, Hcal, u200, pixelQuenteOutput,
				pixelFrioOutput, d0, z0mxy, null , null, true, true);

		//		double H = H(pixelQuenteOutput.getTs(), pixelFrioOutput.getTs(), rahxyCorr);


		//Run HThread
		StringBuilder stringBuilder = createFileLines(image, hOutput);
		createResultsFile(fileName, stringBuilder);
	}

	private StringBuilder createFileLines(Image image, List<HOutput> listHOutput) {
		StringBuilder stringBuilder = new StringBuilder();
		String string = "i,j,lat,lon,hInicial,hFinal,aInicial,aFinal," +
				"bInicial,bFinal,rahInicial,rahFinal,uInicial,uFinal,lInicial,lFinal"
				+",g,rn,lambdaE,Ts,ndvi,savi,alpha\n";
		stringBuilder.append(string);
		StringBuilder stringBuilderElevation = new StringBuilder();
		stringBuilderElevation.append("i,j,lat,lon,elevation\n");
		for (ImagePixel imagePixel : image.pixels()) {
			ImagePixelOutput output = imagePixel.output();
			List<HOutput> hPixel = iterH(imagePixel, listHOutput);
			//			output.setH(hPixel);
			double lambdaE = lambdaE(output);
			output.setLambdaE(lambdaE);
			imagePixel.setOutput(output);
			String elevationOutput = imagePixel.geoLoc().getI() + "," + imagePixel.geoLoc().getJ()
					+ "," + imagePixel.geoLoc().getLat() + "," + imagePixel.geoLoc().getLon()
					+ "," + imagePixel.z() + "\n";
			stringBuilderElevation.append(elevationOutput);
			stringBuilder.append(generateResultLine(imagePixel, hPixel));
		}
		createResultsFile("result3.csv", stringBuilderElevation);
		return stringBuilder;
	}

	public List<HOutput> hOutput(double rahxyCorr, double rahxy, double uAsteriskCorrxy, double Hcal,
			double u200, ImagePixelOutput pixelQuenteOutput, ImagePixelOutput pixelFrioOutput,
			double d0, double z0mxy, Double aQuente, Double bQuente, boolean makeCalculation, boolean isPixelQuente) {

		double H = Hcal;
		int i = 0;
		List<HOutput> listH = new ArrayList<HOutput>();
		while (Math.abs((1. - (rahxyCorr / rahxy)) * 100.0) > 0.01) {
			HOutput hOutput = new HOutput();
			//			System.out.println(i);
			//			double dT = (Hcal * rahxy)/(rho * cp);
			double L = Lxy(uAsteriskCorrxy, pixelQuenteOutput.getTs(), H);
			double psim = psim(L);
			uAsteriskCorrxy = uAsteriskCorrxy(u200, d0, z0mxy, psim);
			double psiH1 = psiH1(L);
			double psiH2 = psiH2(L);
			double dTQuente = dTQuente(rahxy, pixelQuenteOutput.Rn(), pixelQuenteOutput.G());
			bQuente = b(pixelQuenteOutput, pixelFrioOutput, dTQuente); 
			aQuente =  a(bQuente, pixelFrioOutput.getTs() - 273.15);
			H = H(aQuente, bQuente, (pixelQuenteOutput.getTs()- 273.15), rahxy); 
			rahxyCorr = rahxy;
			rahxy = rahCorrxy(psiH1, psiH2, uAsteriskCorrxy);
			//			double ta1 = bQuente * (pixelQuenteOutput.getTs() - 273.15);
			//			double ta2 = aQuente * -1;
			i++;

			hOutput.setA(aQuente);
			hOutput.setB(bQuente);
			hOutput.setH(H);
			listH.add(hOutput);
		}

		return listH;
	}

	public List<HOutput> hOutputPixel(double uAsterisk, double rahxy, double u200, 
			ImagePixelOutput pixelQuenteOutput, ImagePixelOutput pixelFrioOutput,
			double d0, double z0mxy, List<HOutput> listHOutput) {

		double H;
		int i = 0;
		List<HOutput> listH = new ArrayList<HOutput>();
		double uAsteriskCorrxy = uAsterisk;
		double rahCorrxy = rahxy;
		for (HOutput hOut : listHOutput) {

			HOutput hOutput = new HOutput();
			//			System.out.println(i);
			//			double dT = (Hcal * rahxy)/(rho * cp);
			double aQuente =  hOut.getA();
			double bQuente = hOut.getB(); 
			H = H(aQuente, bQuente, (pixelQuenteOutput.getTs()- 273.15), rahCorrxy); 
			double L = Lxy(uAsteriskCorrxy, pixelQuenteOutput.getTs(), H);
			double psim = psim(L);
			uAsteriskCorrxy = uAsteriskCorrxy(u200, d0, z0mxy, psim);
			double psiH1 = psiH1(L);
			double psiH2 = psiH2(L);
			rahCorrxy = rahCorrxy(psiH1, psiH2, uAsteriskCorrxy);
			double dTQuente = dTQuente(rahCorrxy, pixelQuenteOutput.Rn(), pixelQuenteOutput.G());
			i++;

			hOutput.setA(aQuente);
			hOutput.setB(bQuente);
			hOutput.setH(H);
			hOutput.setRah(rahCorrxy);
			hOutput.setuAsterisk(uAsteriskCorrxy);
			hOutput.setL(L);
			listH.add(hOutput);
		}

		return listH;
	}

	public List<HOutput> iterH(ImagePixel imagePixel, List<HOutput> listHOutput) {
		double z0m = z0m(imagePixel.hc());
		double uAsterisk = uAsterisk(imagePixel.ux(), 
				imagePixel.zx(), imagePixel.d(), z0m);
		double u200 = u200(uAsterisk, imagePixel.d(), z0m);
		double d0 = imagePixel.d();

		ImagePixelOutput imagePixelOutput = imagePixel.output();

		double z0mxy = z0mxy(imagePixelOutput.SAVI());
		double uAsteriskxy = uAsteriskxy(u200, d0, z0mxy);
		double rahxy = rahxy(uAsteriskxy);
		imagePixel.output().setZ0mxy(z0mxy);

		int i = 1;
		//TODO configuracao
		//		double H = H(aQuente, bQuente, (imagePixelOutput.getTs() - 273.15), rahxy);

		List<HOutput> hOutput = hOutputPixel(uAsterisk, rahxy, u200, imagePixelOutput,
				imagePixelOutput, d0, z0mxy, listHOutput);
		return hOutput;
	}

	public String generateResultLine(ImagePixel imagePixel, List<HOutput> hOuts) {
		int i = imagePixel.geoLoc().getI();
		int j = imagePixel.geoLoc().getJ();
		double lat = imagePixel.geoLoc().getLat();
		double lon = imagePixel.geoLoc().getLon();
		double g = imagePixel.output().G();
		double rn = imagePixel.output().Rn();
		double lambdaE = lambdaE(imagePixel.output());

		double hFinal = hOuts.get(hOuts.size() - 1).getH();
		double hInicial = hOuts.get(0).getH();
		double aFinal = hOuts.get(hOuts.size() - 1).getA();
		double aInicial = hOuts.get(0).getA();
		double bFinal = hOuts.get(hOuts.size() - 1).getB();
		double bInicial = hOuts.get(0).getB();
		double uFinal = hOuts.get(hOuts.size() - 1).getuAsterisk();
		double uInicial = hOuts.get(0).getuAsterisk();
		double lFinal = hOuts.get(hOuts.size() - 1).getL();
		double lInicial = hOuts.get(0).getL();
		double rahFinal = hOuts.get(hOuts.size() - 1).getRah();
		double rahInicial = hOuts.get(0).getRah();

		return i + "," + j + "," + lat + "," + lon
				+ "," + hInicial + "," + hFinal + "," + aInicial + "," + aFinal + "," + 
				bInicial + "," + bFinal + "," + rahInicial + "," + rahFinal + "," +
				uInicial + "," + uFinal + "," + lInicial + "," + lFinal + "," +g + "," + 
				rn + "," + lambdaE + "," + imagePixel.output().getTs() + "," + imagePixel.output().getNDVI() +  
				"," + imagePixel.output().SAVI() + "," + imagePixel.output().getAlpha() + "," + 
				Arrays.toString(imagePixel.L()) + "," + imagePixel.output().getZ0mxy() + "," + 
				imagePixel.output().getEpsilonZero() + "," + imagePixel.output().getEpsilonNB() + "\n";
	}

	private void createResultsFile(String fileName, StringBuilder stringBuilder) {
		File file = new File(fileName);
		if (file.exists()) {
			file.delete();
		}
		try {
			FileUtils.writeStringToFile(file, stringBuilder.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public double lambdaE(ImagePixelOutput output) {
		return output.Rn() - output.G() - output.getH();
	}

	private double b(ImagePixelOutput pixelQuenteOutput,
			ImagePixelOutput pixelFrioOutput, double dTQuente) {
		return dTQuente / (pixelQuenteOutput.getTs() - pixelFrioOutput.getTs());
	}


	public ImagePixelOutput processPixel(Satellite satellite, ImagePixel imagePixel) {
		ImagePixelOutput output = new ImagePixelOutput();
		double[] LLambda = imagePixel.L();

		double[] rho = new double[7];
		for (int i = 0; i < rho.length; i++) {
			if (i == 5) {
				continue;
			}
			double rhoI = rho(LLambda[i], earthSunDistance.get(imagePixel.image().getDay()), 
					satellite.ESUN(i + 1), imagePixel.cosTheta());
			rho[i] = rhoI;
		}
		//		System.out.println("rho " + Arrays.toString(rho));
		output.setRho(rho);
		double alphaToa = alphaToa(rho[0], rho[1], rho[2], rho[3], rho[4], rho[6]);
		//		System.out.println("alphaToa " + alphaToa);

		double tauSW = tauSW(imagePixel.z());
		//		System.out.println("tauSW " + tauSW);

		double alpha = alpha(alphaToa, tauSW);
		//		System.out.println("alpha " + alpha);

		double RSDown = RSDown(imagePixel.cosTheta(), 
				earthSunDistance.get(imagePixel.image().getDay()), tauSW);


		double NDVI = NDVI(rho[2], rho[3]);
		output.setNDVI(NDVI);
		//		System.out.println("NDVI " + NDVI);

		double SAVI = SAVI(rho[2], rho[3]);
		//		System.out.println("SAVI " + SAVI);
		output.setSAVI(SAVI);

		double EVI = EVI(rho[0], rho[2], rho[3]);
		//		System.out.println("EVI " + EVI);

		double IAF = IAF(SAVI);
		//		System.out.println("IAF " + IAF);

		double epsilonNB = epsilonNB(IAF);
		output.setEpsilonNB(epsilonNB);
		//		System.out.println("epsilonNB " + epsilonNB);

		double epsilonZero = epsilonZero(IAF);
		output.setEpsilonZero(epsilonZero);
		//		System.out.println("epsilonZero " + epsilonZero);

		double TS = TS(satellite.K2(), epsilonNB, satellite.K1(), LLambda[5]);
		output.setTs(TS);
		//		System.out.println("TS " + TS);

		double RLUp = RLUp(epsilonZero, TS);
		//		System.out.println("RLUp " + RLUp);

		double epsilonA = epsilonA(tauSW);
		//		System.out.println("epsilonA " + epsilonA);

		double RLDown = RLDown(epsilonA, imagePixel.Ta());
		//		System.out.println("RLDown " + RLDown);

		double Rn = Rn(alpha, RSDown, RLDown, RLUp, epsilonZero);
		//		System.out.println("Rn " + Rn);
		output.setRn(Rn);
		output.setAlpha(alpha);

		double G = G(TS, alpha, NDVI, Rn);
		//		System.out.println("G " + G);
		output.setG(G);

		return output;
	}
}
