export * from './clavesApi.service';
import { ClavesApiService } from './clavesApi.service';
export * from './consentimientos.service';
import { ConsentimientosService } from './consentimientos.service';
export * from './evaluaciones.service';
import { EvaluacionesService } from './evaluaciones.service';
export * from './solicitantes.service';
import { SolicitantesService } from './solicitantes.service';
export const APIS = [ClavesApiService, ConsentimientosService, EvaluacionesService, SolicitantesService];
