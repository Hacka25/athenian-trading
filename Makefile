default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -xtest

scan:
	./gradlew build --scan -xtest

build: compile

uberjar:
	./gradlew uberjar

uber: uberjar
	java -jar build/libs/server.jar

cc:
	./gradlew build --continuous -x test

run:
	./gradlew run

tests:
	./gradlew check

heroku-logs:
	heroku logs --tail

heroku-open:
	heroku open

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

upgrade-wrapper:
	./gradlew wrapper --gradle-version=6.8.1 --distribution-type=bin