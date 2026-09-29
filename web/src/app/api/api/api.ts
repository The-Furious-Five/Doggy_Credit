export * from './consentimientos.service';
import { ConsentimientosService } from './consentimientos.service';
export * from './evaluaciones.service';
import { EvaluacionesService } from './evaluaciones.service';
export const APIS = [ConsentimientosService, EvaluacionesService];
