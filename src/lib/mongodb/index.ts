import { MongoClient, MongoClientOptions } from 'mongodb';

const options: MongoClientOptions = {};

declare global {
	// eslint-disable-next-line no-var
	var _mongoClientPromise: Promise<MongoClient> | undefined;
}

let cachedPromise: Promise<MongoClient> | undefined;

function getClientPromise(): Promise<MongoClient> {
	if (cachedPromise) return cachedPromise;

	const uri = process.env.MONGODB_URI;
	if (!uri) {
		throw new Error('MONGODB_URI is not set. Set it in .env.local or use BACKEND_URL to skip direct DB access.');
	}

	if (process.env.NODE_ENV === 'development') {
		if (!global._mongoClientPromise) {
			global._mongoClientPromise = new MongoClient(uri, options).connect();
		}
		cachedPromise = global._mongoClientPromise;
	} else {
		cachedPromise = new MongoClient(uri, options).connect();
	}
	return cachedPromise;
}

const clientPromise = getClientPromise;
export default clientPromise;
